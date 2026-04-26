package com.example.airsimapp.Fragments;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;


class ManualFragmentTest {

    @org.junit.jupiter.api.Test
    void stickYToThrottle() {
        // exact bounds
        assertEquals(0, ManualFragment.stickYToThrottle(-1000));
        assertEquals(1000, ManualFragment.stickYToThrottle(1000));

        // center
        assertEquals(500, ManualFragment.stickYToThrottle(0));

        assertEquals(499, ManualFragment.stickYToThrottle(-1));
        assertEquals(500, ManualFragment.stickYToThrottle(1));
        assertEquals(499, ManualFragment.stickYToThrottle(-2));
        assertEquals(501, ManualFragment.stickYToThrottle(2));

        assertEquals(250, ManualFragment.stickYToThrottle(-500));
        assertEquals(750, ManualFragment.stickYToThrottle(500));

        // normla vals
        assertEquals(50, ManualFragment.stickYToThrottle(-900));
        assertEquals(100, ManualFragment.stickYToThrottle(-800));
        assertEquals(150, ManualFragment.stickYToThrottle(-700));
        assertEquals(200, ManualFragment.stickYToThrottle(-600));
        assertEquals(300, ManualFragment.stickYToThrottle(-400));
        assertEquals(350, ManualFragment.stickYToThrottle(-300));
        assertEquals(400, ManualFragment.stickYToThrottle(-200));
        assertEquals(450, ManualFragment.stickYToThrottle(-100));
        assertEquals(550, ManualFragment.stickYToThrottle(100));
        assertEquals(600, ManualFragment.stickYToThrottle(200));
        assertEquals(650, ManualFragment.stickYToThrottle(300));
        assertEquals(700, ManualFragment.stickYToThrottle(400));
        assertEquals(800, ManualFragment.stickYToThrottle(600));
        assertEquals(850, ManualFragment.stickYToThrottle(700));
        assertEquals(900, ManualFragment.stickYToThrottle(800));
        assertEquals(950, ManualFragment.stickYToThrottle(900));

        // min bounds testing
        assertEquals(0, ManualFragment.stickYToThrottle(-999));
        assertEquals(1, ManualFragment.stickYToThrottle(-998));
        assertEquals(1, ManualFragment.stickYToThrottle(-997));
        assertEquals(2, ManualFragment.stickYToThrottle(-996));
        assertEquals(2, ManualFragment.stickYToThrottle(-995));

        //  max bounds testing
        assertEquals(999, ManualFragment.stickYToThrottle(998));
        assertEquals(999, ManualFragment.stickYToThrottle(999));
        assertEquals(998, ManualFragment.stickYToThrottle(997));
        assertEquals(998, ManualFragment.stickYToThrottle(996));
        assertEquals(997, ManualFragment.stickYToThrottle(995));

        // potential odd and even checks
        assertEquals(5, ManualFragment.stickYToThrottle(-990));
        assertEquals(10, ManualFragment.stickYToThrottle(-980));
        assertEquals(15, ManualFragment.stickYToThrottle(-970));
        assertEquals(20, ManualFragment.stickYToThrottle(-960));
        assertEquals(25, ManualFragment.stickYToThrottle(-950));

        assertEquals(975, ManualFragment.stickYToThrottle(950));
        assertEquals(980, ManualFragment.stickYToThrottle(960));
        assertEquals(985, ManualFragment.stickYToThrottle(970));
        assertEquals(990, ManualFragment.stickYToThrottle(980));
        assertEquals(995, ManualFragment.stickYToThrottle(990));

        // random values in range to make sure it works
        assertEquals(438, ManualFragment.stickYToThrottle(-125));
        assertEquals(562, ManualFragment.stickYToThrottle(125));
        assertEquals(389, ManualFragment.stickYToThrottle(-222));
        assertEquals(611, ManualFragment.stickYToThrottle(222));
        assertEquals(167, ManualFragment.stickYToThrottle(-666));
        assertEquals(833, ManualFragment.stickYToThrottle(666));
        assertEquals(61, ManualFragment.stickYToThrottle(-878));
        assertEquals(939, ManualFragment.stickYToThrottle(878));

        // outside of range a little
        assertEquals(0, ManualFragment.stickYToThrottle(-1001));
        assertEquals(1000, ManualFragment.stickYToThrottle(1001));

        // outside of potential rang ebut far
        assertEquals(0, ManualFragment.stickYToThrottle(-1100));
        assertEquals(1000, ManualFragment.stickYToThrottle(1100));
        assertEquals(0, ManualFragment.stickYToThrottle(-1500));
        assertEquals(1000, ManualFragment.stickYToThrottle(1500));
        assertEquals(0, ManualFragment.stickYToThrottle(-2000));
        assertEquals(1000, ManualFragment.stickYToThrottle(2000));
        assertEquals(0, ManualFragment.stickYToThrottle(Integer.MIN_VALUE));
        assertEquals(1000, ManualFragment.stickYToThrottle(Integer.MAX_VALUE));

    }

    @org.junit.jupiter.api.Test
    void clamp() {


        assertEquals(5, ManualFragment.clamp(5, 0, 10));
        assertEquals(0, ManualFragment.clamp(0, 0, 10));
        assertEquals(10, ManualFragment.clamp(10, 0, 10));
        assertEquals(7, ManualFragment.clamp(7, 0, 10));
        assertEquals(3, ManualFragment.clamp(3, 0, 10));

        // less than potential value range
        assertEquals(0, ManualFragment.clamp(-1, 0, 10));
        assertEquals(0, ManualFragment.clamp(-5, 0, 10));
        assertEquals(0, ManualFragment.clamp(-10, 0, 10));
        assertEquals(0, ManualFragment.clamp(-100, 0, 10));
        assertEquals(0, ManualFragment.clamp(Integer.MIN_VALUE, 0, 10));

        // + range
        assertEquals(10, ManualFragment.clamp(11, 0, 10));
        assertEquals(10, ManualFragment.clamp(15, 0, 10));
        assertEquals(10, ManualFragment.clamp(100, 0, 10));
        assertEquals(10, ManualFragment.clamp(1000, 0, 10));
        assertEquals(10, ManualFragment.clamp(Integer.MAX_VALUE, 0, 10));

        // - range
        assertEquals(-5, ManualFragment.clamp(-5, -10, -1));
        assertEquals(-10, ManualFragment.clamp(-15, -10, -1));
        assertEquals(-1, ManualFragment.clamp(0, -10, -1));
        assertEquals(-10, ManualFragment.clamp(-10, -10, -1));
        assertEquals(-1, ManualFragment.clamp(-1, -10, -1));
        assertEquals(-7, ManualFragment.clamp(-7, -10, -1));
        assertEquals(-3, ManualFragment.clamp(-3, -10, -1));

        // mixed ranges given
        assertEquals(0, ManualFragment.clamp(0, -10, 10));
        assertEquals(-10, ManualFragment.clamp(-11, -10, 10));
        assertEquals(10, ManualFragment.clamp(11, -10, 10));
        assertEquals(-10, ManualFragment.clamp(-10, -10, 10));
        assertEquals(10, ManualFragment.clamp(10, -10, 10));
        assertEquals(-5, ManualFragment.clamp(-5, -10, 10));
        assertEquals(5, ManualFragment.clamp(5, -10, 10));

        // single range
        assertEquals(5, ManualFragment.clamp(5, 5, 5));
        assertEquals(5, ManualFragment.clamp(4, 5, 5));
        assertEquals(5, ManualFragment.clamp(6, 5, 5));
        assertEquals(5, ManualFragment.clamp(Integer.MIN_VALUE, 5, 5));
        assertEquals(5, ManualFragment.clamp(Integer.MAX_VALUE, 5, 5));

        // mreo ranges
        assertEquals(50, ManualFragment.clamp(50, 0, 100));
        assertEquals(0, ManualFragment.clamp(-50, 0, 100));
        assertEquals(100, ManualFragment.clamp(150, 0, 100));
        assertEquals(99, ManualFragment.clamp(99, 0, 100));
        assertEquals(1, ManualFragment.clamp(1, 0, 100));

        // exact
        assertEquals(-100, ManualFragment.clamp(-100, -100, 100));
        assertEquals(100, ManualFragment.clamp(100, -100, 100));
        assertEquals(-100, ManualFragment.clamp(-101, -100, 100));
        assertEquals(100, ManualFragment.clamp(101, -100, 100));

        // testing zero
        assertEquals(-1, ManualFragment.clamp(-1, -5, 5));
        assertEquals(0, ManualFragment.clamp(0, -5, 5));
        assertEquals(1, ManualFragment.clamp(1, -5, 5));
        assertEquals(-5, ManualFragment.clamp(-6, -5, 5));
        assertEquals(5, ManualFragment.clamp(6, -5, 5));

        // custom bounds at max
        assertEquals(500000, ManualFragment.clamp(500000, 100000, 900000));
        assertEquals(100000, ManualFragment.clamp(99999, 100000, 900000));
        assertEquals(900000, ManualFragment.clamp(900001, 100000, 900000));
        assertEquals(100000, ManualFragment.clamp(Integer.MIN_VALUE, 100000, 900000));
        assertEquals(900000, ManualFragment.clamp(Integer.MAX_VALUE, 100000, 900000));

        // edge bounds
        assertEquals(Integer.MIN_VALUE, ManualFragment.clamp(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, ManualFragment.clamp(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertEquals(0, ManualFragment.clamp(0, Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertEquals(-1, ManualFragment.clamp(-1, Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertEquals(1, ManualFragment.clamp(1, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    @org.junit.jupiter.api.Test
    void polarToXY() {

        assertArrayEquals(new int[]{0, 0}, ManualFragment.polarToXY(0, 4, 5));
        assertArrayEquals(new int[]{0, 0}, ManualFragment.polarToXY(90, 3, 5));
        assertArrayEquals(new int[]{0, 0}, ManualFragment.polarToXY(180, 0, 5));
        assertArrayEquals(new int[]{0, 0}, ManualFragment.polarToXY(270, 2, 5));

        // exactly at deadzone should still be counted for sure
        assertArrayEquals(new int[]{50, 0}, ManualFragment.polarToXY(0, 5, 5));
        assertArrayEquals(new int[]{0, -50}, ManualFragment.polarToXY(90, 5, 5));
        assertArrayEquals(new int[]{-50, 0}, ManualFragment.polarToXY(180, 5, 5));
        assertArrayEquals(new int[]{0, 50}, ManualFragment.polarToXY(270, 5, 5));

        // full strength
        assertArrayEquals(new int[]{1000, 0}, ManualFragment.polarToXY(0, 100, 0));
        assertArrayEquals(new int[]{0, -1000}, ManualFragment.polarToXY(90, 100, 0));
        assertArrayEquals(new int[]{-1000, 0}, ManualFragment.polarToXY(180, 100, 0));
        assertArrayEquals(new int[]{0, 1000}, ManualFragment.polarToXY(270, 100, 0));
        assertArrayEquals(new int[]{1000, 0}, ManualFragment.polarToXY(360, 100, 0));

        // half strength
        assertArrayEquals(new int[]{500, 0}, ManualFragment.polarToXY(0, 50, 0));
        assertArrayEquals(new int[]{0, -500}, ManualFragment.polarToXY(90, 50, 0));
        assertArrayEquals(new int[]{-500, 0}, ManualFragment.polarToXY(180, 50, 0));
        assertArrayEquals(new int[]{0, 500}, ManualFragment.polarToXY(270, 50, 0));

        // quarter strength
        assertArrayEquals(new int[]{250, 0}, ManualFragment.polarToXY(0, 25, 0));
        assertArrayEquals(new int[]{0, -250}, ManualFragment.polarToXY(90, 25, 0));
        assertArrayEquals(new int[]{-250, 0}, ManualFragment.polarToXY(180, 25, 0));
        assertArrayEquals(new int[]{0, 250}, ManualFragment.polarToXY(270, 25, 0));

        //  full strength diagonal
        assertArrayEquals(new int[]{707, -707}, ManualFragment.polarToXY(45, 100, 0));
        assertArrayEquals(new int[]{-707, -707}, ManualFragment.polarToXY(135, 100, 0));
        assertArrayEquals(new int[]{-707, 707}, ManualFragment.polarToXY(225, 100, 0));
        assertArrayEquals(new int[]{707, 707}, ManualFragment.polarToXY(315, 100, 0));

        // half strength diagonal
        assertArrayEquals(new int[]{354, -354}, ManualFragment.polarToXY(45, 50, 0));
        assertArrayEquals(new int[]{-354, -354}, ManualFragment.polarToXY(135, 50, 0));
        assertArrayEquals(new int[]{-354, 354}, ManualFragment.polarToXY(225, 50, 0));
        assertArrayEquals(new int[]{354, 354}, ManualFragment.polarToXY(315, 50, 0));

        assertArrayEquals(new int[]{1000, 0}, ManualFragment.polarToXY(720, 100, 0));
        assertArrayEquals(new int[]{0, -1000}, ManualFragment.polarToXY(450, 100, 0));   // 90 + 360
        assertArrayEquals(new int[]{0, 1000}, ManualFragment.polarToXY(-90, 100, 0));
        assertArrayEquals(new int[]{-1000, 0}, ManualFragment.polarToXY(-180, 100, 0));

        // no deadzone
        assertArrayEquals(new int[]{10, 0}, ManualFragment.polarToXY(0, 1, 0));
        assertArrayEquals(new int[]{0, -10}, ManualFragment.polarToXY(90, 1, 0));
        assertArrayEquals(new int[]{-10, 0}, ManualFragment.polarToXY(180, 1, 0));
        assertArrayEquals(new int[]{0, 10}, ManualFragment.polarToXY(270, 1, 0));

        assertArrayEquals(new int[]{130, 0}, ManualFragment.polarToXY(0, 12.6, 0));
        assertArrayEquals(new int[]{120, 0}, ManualFragment.polarToXY(0, 12.4, 0));
        assertArrayEquals(new int[]{0, -130}, ManualFragment.polarToXY(90, 12.6, 0));
        assertArrayEquals(new int[]{0, -120}, ManualFragment.polarToXY(90, 12.4, 0));

        assertArrayEquals(new int[]{1000, 0}, ManualFragment.polarToXY(0, 150, 0));
        assertArrayEquals(new int[]{0, -1000}, ManualFragment.polarToXY(90, 150, 0));
        assertArrayEquals(new int[]{-1000, 0}, ManualFragment.polarToXY(180, 500, 0));
        assertArrayEquals(new int[]{0, 1000}, ManualFragment.polarToXY(270, 1000, 0));


        assertArrayEquals(new int[]{0, 0}, ManualFragment.polarToXY(0, -1, 0));
        assertArrayEquals(new int[]{0, 0}, ManualFragment.polarToXY(90, -10, 0));
        assertArrayEquals(new int[]{0, 0}, ManualFragment.polarToXY(180, -50, 0));

        // potential angles
        assertArrayEquals(new int[]{866, -500}, ManualFragment.polarToXY(30, 100, 0));
        assertArrayEquals(new int[]{500, -866}, ManualFragment.polarToXY(60, 100, 0));
        assertArrayEquals(new int[]{-500, -866}, ManualFragment.polarToXY(120, 100, 0));
        assertArrayEquals(new int[]{-866, -500}, ManualFragment.polarToXY(150, 100, 0));
        assertArrayEquals(new int[]{-866, 500}, ManualFragment.polarToXY(210, 100, 0));
        assertArrayEquals(new int[]{-500, 866}, ManualFragment.polarToXY(240, 100, 0));
        assertArrayEquals(new int[]{500, 866}, ManualFragment.polarToXY(300, 100, 0));
        assertArrayEquals(new int[]{866, 500}, ManualFragment.polarToXY(330, 100, 0));

        // test if deadzone larger than strength
        assertArrayEquals(new int[]{0, 0}, ManualFragment.polarToXY(45, 49, 50));
        assertArrayEquals(new int[]{354, -354}, ManualFragment.polarToXY(45, 50, 50));
        assertArrayEquals(new int[]{361, -361}, ManualFragment.polarToXY(45, 51, 50));

        // very large deadzone can still be allowed as long as strength is either bigger than or equal to deadzone
        // we also make sure mag is clamped to 100 max
        assertArrayEquals(new int[]{0, 0}, ManualFragment.polarToXY(0, 99, 100));
        assertArrayEquals(new int[]{1000, 0}, ManualFragment.polarToXY(0, 100, 100));
        assertArrayEquals(new int[]{1000, 0}, ManualFragment.polarToXY(0, 150, 100));

    }
}