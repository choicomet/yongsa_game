package com.yongsa.model;

/** 이번 프레임의 입력. 화면이 키보드에서 읽어 GameSession 에 넘긴다. */
public class Controls {
    public float moveX, moveY;
    public boolean attack, roll, interact;
    /** 검 스킬 (Q). */
    public boolean skill;
    /** 포션 마시기 (R). */
    public boolean potion;

    public void clear() {
        moveX = moveY = 0;
        attack = roll = interact = skill = potion = false;
    }
}
