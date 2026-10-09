package com.yongsa.world;

/** 한 시뮬레이션 간격 동안의 입력. 화면에서 채워 GameWorld 에 넘긴다. */
public class PlayerInput {
    public float moveX, moveY;
    /** 마우스가 가리키는 월드 좌표. */
    public float aimX, aimY;
    public boolean attackHeld;
    public boolean interactHeld;
    /** 이번 간격에 새로 눌렸는지(한 번만 처리). */
    public boolean interactPressed;
    public boolean shockwavePressed;

    public void clearEdges() {
        interactPressed = false;
        shockwavePressed = false;
    }
}
