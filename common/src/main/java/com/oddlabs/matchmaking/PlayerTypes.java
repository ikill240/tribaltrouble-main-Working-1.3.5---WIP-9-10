package com.oddlabs.matchmaking;

public enum PlayerTypes {
    None,
    Human,
    AIEasy,
    AINormal,
    AIHard,
    // Appended at the end rather than inserted alongside AIHard - if this enum is ever serialized
    // by ordinal (network/matchmaking protocols often are), inserting in the middle would shift
    // every value after it and silently corrupt anything already relying on the old ordinals.
    // //added by ikill240c
    AIInsane //added by ikill240c
}
