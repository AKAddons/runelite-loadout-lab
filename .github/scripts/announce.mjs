// Summarize the just-pushed commits and announce them to a Discord channel.
//
// Runs inside the "Announce pushes to Discord" GitHub Action. It reads the push
// event payload GitHub drops on disk, asks the Claude API for a single
// player-facing summary of the whole push, and posts one rich embed via a
// Discord webhook. If the Claude key is absent it degrades to a plain (non-AI)
// commit list; if the webhook is absent it exits quietly (e.g. on forks).
//
// Env:
//   DISCORD_WEBHOOK_URL  (required to post)   Discord channel webhook URL
//   ANTHROPIC_API_KEY    (optional)           enables AI summaries
//   ANTHROPIC_MODEL      (optional)           model id; default claude-haiku-4-5
//   GITHUB_EVENT_PATH    (set by Actions)     path to the push event JSON
//   GITHUB_WORKSPACE     (set by Actions)     repo root (for git commands)

import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import Anthropic from "@anthropic-ai/sdk";

const ZERO_SHA = "0000000000000000000000000000000000000000";
const FEATURES_PATH = "docs/features.json";
const MAX_SUMMARY_CHARS = 1200;

const WEBHOOK = process.env.DISCORD_WEBHOOK_URL;
const REPO_ROOT = process.env.GITHUB_WORKSPACE || process.cwd();

const log = (msg) => console.log(`[announce] ${msg}`);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

if (!WEBHOOK) {
  log("DISCORD_WEBHOOK_URL is not set — nothing to post. Exiting cleanly.");
  process.exit(0);
}

// ---------------------------------------------------------------------------
// Load the push event GitHub handed us.
// ---------------------------------------------------------------------------
const event = JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH, "utf8"));

const repoFullName = event.repository?.full_name ?? "unknown/repo";
const repoName = event.repository?.name ?? repoFullName;
const branch = (event.ref ?? "").replace(/^refs\/heads\//, "") || "unknown";
const pusher = event.pusher?.name ?? event.sender?.login ?? "someone";
const compareUrl = event.compare ?? event.repository?.html_url ?? "";
const before = event.before ?? ZERO_SHA;
const after = event.after ?? ZERO_SHA;

// GitHub lists commits oldest → newest; show newest first.
const commits = [...(event.commits ?? [])].reverse();
if (commits.length === 0) {
  log("Push contained no commits (branch delete or tag). Exiting cleanly.");
  process.exit(0);
}

// Distinct files touched across the push.
const changedFiles = new Set();
for (const c of commits) {
  for (const f of [...(c.added ?? []), ...(c.modified ?? []), ...(c.removed ?? [])]) {
    changedFiles.add(f);
  }
}
const featuresChanged = changedFiles.has(FEATURES_PATH);
const hasFeatCommit = commits.some((c) => /^feat(\(|:|!)/i.test(c.message ?? ""));

// ---------------------------------------------------------------------------
// Detect new features by diffing the feature registry across the push.
// ---------------------------------------------------------------------------
function gitShow(ref, path) {
  try {
    return execFileSync("git", ["show", `${ref}:${path}`], {
      cwd: REPO_ROOT,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    });
  } catch {
    return null; // path didn't exist at that ref, or ref unavailable
  }
}

function readFeatures(text) {
  if (!text) return null;
  try {
    const map = new Map();
    for (const f of JSON.parse(text).features ?? []) {
      if (f && f.id) map.set(f.id, { title: f.title ?? f.id, since: f.since ?? null });
    }
    return map;
  } catch {
    return null;
  }
}

// Titles of features that are newly present after this push.
function detectNewFeatures() {
  if (!featuresChanged || before === ZERO_SHA) return [];
  const beforeMap = readFeatures(gitShow(before, FEATURES_PATH));
  const afterMap = readFeatures(gitShow(after, FEATURES_PATH));
  if (!beforeMap || !afterMap) return [];
  const added = [];
  for (const [id, f] of afterMap) if (!beforeMap.has(id)) added.push(f);
  return added;
}

const newFeatures = detectNewFeatures();

// ---------------------------------------------------------------------------
// Build a summary — via Claude when we have a key, else a plain fallback.
// ---------------------------------------------------------------------------
const SYSTEM = `You write short, friendly release notes for the dev-feed channel of the Loadout Lab Discord. Loadout Lab is a RuneLite (Old School RuneScape) plugin that finds the best-in-slot gear a player already owns. The readers are players, not programmers.

Rules:
- Output GitHub-flavored Markdown only. No preamble, no sign-off, no headings.
- First line: a single bolded sentence summarizing the push overall.
- Then up to 6 concise bullet points ("- ") on user-facing changes and, briefly, why each matters. Fold trivial internal chores into at most one bullet, or omit them when there are user-facing changes.
- Translate developer jargon into plain language. Never mention file names, function names, or commit hashes.
- Only describe changes actually present in the input. Never speculate or invent.
- Keep the whole thing under ${MAX_SUMMARY_CHARS} characters.`;

function buildPrompt() {
  const lines = [];
  lines.push(`Repository: ${repoFullName} (branch ${branch})`);
  lines.push(`Pushed by: ${pusher}`);
  lines.push(`${commits.length} commit(s), ${changedFiles.size} file(s) changed.`);
  lines.push("");
  lines.push("Commits (newest first):");
  for (const c of commits) {
    const [subject, ...bodyLines] = (c.message ?? "").split("\n");
    const author = c.author?.username || c.author?.name || "unknown";
    lines.push(`- ${subject} [${author}]`);
    const body = bodyLines.join("\n").trim();
    if (body) lines.push(body.replace(/^/gm, "    "));
  }
  if (newFeatures.length) {
    lines.push("");
    lines.push("Newly added features in the plugin's feature registry:");
    for (const f of newFeatures) {
      lines.push(`- ${f.title}${f.since ? ` (since v${f.since})` : ""}`);
    }
  }
  lines.push("");
  lines.push("Files changed:");
  const fileList = [...changedFiles];
  for (const f of fileList.slice(0, 40)) lines.push(`- ${f}`);
  if (fileList.length > 40) lines.push(`- …and ${fileList.length - 40} more`);
  return lines.join("\n");
}

function plainSummary() {
  const verb = commits.length === 1 ? "commit" : "commits";
  const parts = [`**${pusher} pushed ${commits.length} ${verb} to \`${branch}\`.**`];
  if (newFeatures.length) {
    parts.push("");
    for (const f of newFeatures) {
      parts.push(`✨ New feature: **${f.title}**${f.since ? ` (v${f.since})` : ""}`);
    }
  }
  return parts.join("\n");
}

async function claudeSummary() {
  const client = new Anthropic(); // reads ANTHROPIC_API_KEY
  const model = process.env.ANTHROPIC_MODEL || "claude-haiku-4-5";
  const msg = await client.messages.create({
    model,
    max_tokens: 1024,
    system: SYSTEM,
    messages: [{ role: "user", content: buildPrompt() }],
  });
  return msg.content
    .filter((b) => b.type === "text")
    .map((b) => b.text)
    .join("")
    .trim();
}

let summary;
if (process.env.ANTHROPIC_API_KEY) {
  try {
    summary = await claudeSummary();
    log("Generated summary via Claude.");
  } catch (err) {
    log(`Claude summary failed (${err?.message ?? err}); using plain fallback.`);
    summary = plainSummary();
  }
} else {
  log("ANTHROPIC_API_KEY not set — using plain (non-AI) summary.");
  summary = plainSummary();
}

// Discord embed descriptions cap at 4096 chars; keep well under.
if (summary.length > 3800) summary = summary.slice(0, 3797) + "…";

// ---------------------------------------------------------------------------
// Assemble and post the Discord embed.
// ---------------------------------------------------------------------------
const truncateField = (str, limit = 1024) =>
  str.length > limit ? str.slice(0, limit - 1) + "…" : str;

// Commit list field: linked short shas + subjects. Cap to fit Discord's limit.
const commitLines = [];
let overflow = 0;
for (const c of commits) {
  const shortSha = (c.id ?? "").slice(0, 7);
  const subject = (c.message ?? "").split("\n")[0];
  const author = c.author?.username || c.author?.name || "unknown";
  const line = `[\`${shortSha}\`](${c.url}) ${subject} — ${author}`;
  if (commitLines.join("\n").length + line.length + 1 > 980) {
    overflow++;
    continue;
  }
  commitLines.push(line);
}
if (overflow > 0) commitLines.push(`_…and ${overflow} more_`);

const fields = [
  {
    name: commits.length === 1 ? "Commit" : `Commits (${commits.length})`,
    value: truncateField(commitLines.join("\n")),
  },
];

if (newFeatures.length) {
  fields.push({
    name: newFeatures.length === 1 ? "New feature" : "New features",
    value: truncateField(
      newFeatures
        .map((f) => `✨ **${f.title}**${f.since ? ` — v${f.since}` : ""}`)
        .join("\n"),
    ),
  });
}

// Green when the push adds features, blurple otherwise.
const color = newFeatures.length || hasFeatCommit ? 0x57f287 : 0x5865f2;

const titleVerb = commits.length === 1 ? "1 new commit" : `${commits.length} new commits`;
const embed = {
  title: `🚀 ${titleVerb} on ${repoName}/${branch}`,
  url: compareUrl || undefined,
  description: summary,
  color,
  fields,
  footer: { text: `${repoFullName} • pushed by ${pusher}` },
  timestamp: event.head_commit?.timestamp || new Date().toISOString(),
};

const payload = {
  username: "Loadout Lab",
  embeds: [embed],
  allowed_mentions: { parse: [] }, // don't let commit-message @mentions ping
};

async function post() {
  for (let attempt = 0; attempt < 3; attempt++) {
    const res = await fetch(WEBHOOK, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (res.ok) return true;
    if (res.status === 429) {
      const body = await res.json().catch(() => ({}));
      const wait = Math.ceil((body.retry_after ?? 1) * 1000) + 250;
      log(`Rate limited by Discord; waiting ${wait}ms.`);
      await sleep(wait);
      continue;
    }
    const body = await res.text().catch(() => "");
    log(`Discord webhook returned ${res.status}: ${body}`);
    return false;
  }
  return false;
}

if (await post()) {
  log(`Posted announcement to Discord (${commits.length} commit(s)).`);
} else {
  process.exit(1);
}
