# Discord push announcer

Every push to `main` is summarized by the Claude API and posted as a single rich
embed to a Discord channel, so the community can follow repo progress without
opening GitHub. New entries in `docs/features.json` get a dedicated
"✨ New feature" call-out.

- Workflow: [`.github/workflows/discord-announce.yml`](../workflows/discord-announce.yml)
- Script: [`announce.mjs`](announce.mjs)

## One-time setup

### 1. Create a Discord channel webhook

In Discord: **Server Settings → Integrations → Webhooks → New Webhook**. Point it
at the channel you want the updates in (e.g. `#dev-feed`), give it a name, and
**Copy Webhook URL**. It looks like:

```
https://discord.com/api/webhooks/<id>/<token>
```

Anyone with this URL can post to the channel — treat it as a secret.

### 2. Add the repo secrets

In GitHub: **Settings → Secrets and variables → Actions → New repository secret**.

| Name                  | Value                                              | Required |
| --------------------- | -------------------------------------------------- | -------- |
| `DISCORD_WEBHOOK_URL` | The webhook URL from step 1                         | Yes      |
| `ANTHROPIC_API_KEY`   | An Anthropic API key (for AI summaries)             | Yes\*    |

\* Without `ANTHROPIC_API_KEY` the bot still posts, but with a plain commit list
instead of an AI-written summary.

### 3. (Optional) Change the model

The summarizer uses **`claude-haiku-4-5`** by default — cheap and plenty capable
for a per-push feed. To use a stronger model, add a repository **variable** (not
a secret) under the same settings page:

- **Settings → Secrets and variables → Actions → Variables → New variable**
- Name: `ANTHROPIC_MODEL`  Value: `claude-opus-4-8`

## Behavior notes

- Triggers only on pushes to `main` (edit the `branches:` list in the workflow to
  widen it).
- Posts one embed per push, summarizing all its commits, with a linked commit list.
- A push with no commits (branch delete / tag) is skipped.
- If the Claude call fails, it falls back to a plain commit list rather than
  failing the run.
- `@mentions` in commit messages won't ping the channel.

## Test it locally

```sh
cd .github/scripts
npm install
DISCORD_WEBHOOK_URL="<your test webhook>" \
GITHUB_EVENT_PATH=/path/to/a/push-event.json \
GITHUB_WORKSPACE="$(git rev-parse --show-toplevel)" \
node announce.mjs
```

Leave `ANTHROPIC_API_KEY` unset to exercise the plain-summary path.
