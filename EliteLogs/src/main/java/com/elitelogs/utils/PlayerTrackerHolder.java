package com.elitelogs.utils; public class PlayerTrackerHolder { private static PlayerTracker I; public static void set(PlayerTracker t){I=t;} public static PlayerTracker get(){return I;} }
