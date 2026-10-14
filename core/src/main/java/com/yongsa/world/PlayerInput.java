package com.yongsa.world;

/** 한 시뮬레이션 간격 동안의 입력. 화면에서 채워 GameWorld 에 넘긴다. */
public class PlayerInput {
    public float moveX, moveY;
    /** 테스트/리플레이용 명시적 조준 좌표. 실제 플레이는 keyboardAim을 사용한다. */
    public float aimX, aimY;
    public boolean keyboardAim;
    public boolean attackHeld;
    public boolean interactHeld;
    /** 이번 간격에 새로 눌렸는지(한 번만 처리). */
    public boolean interactPressed;
    public boolean shockwavePressed;
    public boolean attackPressed;
    public boolean dodgePressed;

    public void clearEdges() {
        interactPressed = false;
        shockwavePressed = false;
        attackPressed = false;
        dodgePressed = false;
    }
}
