package com.loadoutlab.command;

import java.util.function.BooleanSupplier;

/**
 * The one {@link Command} factory the engine records with: a label and
 * two lambdas. Each command captures the before-state it needs to
 * revert AT CONSTRUCTION (per the Command contract), so build the
 * command at the moment of the user's action and hand it straight to
 * {@link CommandHistory#execute}.
 *
 * <p>The store-backed factories that once lived here (the flag toggles,
 * pins, notes, mob exclusions, sims, filter items) had no main-source
 * caller: the plugin's StoreOps built them only to call apply() and
 * throw the revert away, since the ENGINE's history is the one live
 * stack. They live on verbatim in the test tree's TestCommands, so main
 * source stays under the Plugin Hub bot's token budget.
 */
public class Commands {

    /** A Command from two lambdas plus a label - the shape every factory returns. */
    public static Command of(String description, BooleanSupplier apply, BooleanSupplier revert) {
        return new Command() {
            public boolean apply() { return apply.getAsBoolean(); }
            public boolean revert() { return revert.getAsBoolean(); }
            public String getDescription() { return description; }
        };
    }
}
