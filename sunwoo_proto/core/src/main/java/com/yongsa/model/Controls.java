package com.yongsa.model;

/** 한 프레임 입력. 화면이 키보드를 읽어 채우고, GameSession은 Gdx.input을 모른다. */
public class Controls {
    public float moveX, moveY;
    /** 아래는 "이번 프레임에 눌렀는지" */
    public boolean attack, whirl, dash, dodge, interact;

    public void clearPresses() {
        attack = whirl = dash = dodge = interact = false;
    }

    public void clearAll() {
        moveX = moveY = 0;
        clearPresses();
    }
}
