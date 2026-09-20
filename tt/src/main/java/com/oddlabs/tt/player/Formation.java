package com.oddlabs.tt.player;

// The formation shapes, matching the classic RTS convention (Age of Empires 2, etc), plus DEFAULT
// (added by ikill240c) which restores the original pre-formation-system behavior: units are just
// assigned the nearest N valid cells found by a spiral scan around the destination, in whatever
// order the scan happens to find them - no deliberate shape at all. Purely a client-side move-order
// dispatch preference - see FormationLayout for the actual offset math and
// Player.setLandscapeTarget()/queueLandscapeTarget() for where it's applied. //added by ikill240c
public enum Formation {
    DEFAULT, //added by ikill240c
    SQUARE,
    DIAMOND,
    CIRCLE,
    STAR, //added by ikill240c
    TIGHT,
    LOOSE,
    BY_TYPE //added by ikill240c
}
