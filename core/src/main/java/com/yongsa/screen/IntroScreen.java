package com.yongsa.screen;

import com.yongsa.YongsaGame;

/** 시작 이야기 (UI-02, FR-공통-01): 그림 3장 + 게임 소개. 아무 키나 다음, Esc 건너뛰기. */
public final class IntroScreen {

    public static final StoryScreen.Page[] PAGES = {
            new StoryScreen.Page("intro1.jpg",
                    "평화롭던 마을, 광장의 오래된 우물에서 어느 날 해골의 왕이 솟아올랐다.\n스스로를 마왕이라 부르는 자였다."),
            new StoryScreen.Page("intro2.jpg",
                    "마왕은 마을 사람들을 보랏빛 수정 속에 가두고 그 생명을 빨아들이기 시작했다."),
            new StoryScreen.Page("intro3.jpg",
                    "남은 것은 가보로 물려받은 봉인된 검을 지닌 용사 하나.\n용사는 우물 아래, 마왕의 던전으로 내려갔다."),
            new StoryScreen.Page(null,
                    "대장장이도, 광부도, 함께 싸울 동료도 없다. 그래서 용사가 다 한다.\n"
                            + "세 던전의 주인을 쓰러뜨려 힘을 모으면 봉인된 검은 성검으로 깨어나리라."),
    };

    private IntroScreen() {
    }

    public static StoryScreen create(YongsaGame game) {
        return new StoryScreen(game, PAGES, "bgm_title", () -> game.startGame(game.createSession()));
    }
}
