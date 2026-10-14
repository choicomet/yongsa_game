package com.yongsa.ui;

import com.yongsa.config.Balance;
import com.yongsa.model.GameState;

/** 창에서 게임 쪽으로 요청하는 기능. 창은 규칙 함수를 호출하고 결과만 표시한다. */
public interface GameActions {
    Balance balance();

    GameState state();

    boolean inCombat();

    /** 상태 변경 직후 저장. 실패하면 false (메모리 상태는 유지). */
    boolean save();

    void sfx(String id);

    /** 무기 아이콘 (없으면 null). */
    default com.badlogic.gdx.graphics.g2d.TextureRegion weaponIcon(String type) {
        return null;
    }
}
