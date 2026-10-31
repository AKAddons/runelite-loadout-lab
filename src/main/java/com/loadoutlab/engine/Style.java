// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import lombok.Getter;

@Getter
public enum Style {
ANY("Any"), MELEE("Melee"), RANGED("Ranged"), MAGIC("Magic");

final String label;

Style(String label) { this.label = label; }

public static Style[] concreteValues() {
    return new Style[]{MELEE, RANGED, MAGIC};
}

public String toString() { return label; }
}
