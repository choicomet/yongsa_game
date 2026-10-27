package com.yongsa.save;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * 저장 칸 1개 (FR-저장-01). 위치: 사용자 폴더/.yongsa/save.json
 * 임시 파일에 먼저 쓰고 바꿔치기해서 쓰는 도중 꺼져도 이전 저장이 남는다 (NFR-11).
 */
public class SaveManager {

    private static final String FILE = ".yongsa/save.json";
    private static final String TEMP = ".yongsa/save.json.tmp";

    /** 불러오기 결과: 저장 없음 / 정상 / 깨짐. */
    public record Loaded(SaveData data, boolean broken) {
    }

    public boolean hasSave() {
        return Gdx.files.external(FILE).exists();
    }

    public void save(SaveData data) {
        FileHandle temp = Gdx.files.external(TEMP);
        temp.writeString(SaveMapper.toJson(data), false, "UTF-8");
        FileHandle target = Gdx.files.external(FILE);
        if (target.exists()) target.delete();
        temp.moveTo(target);
    }

    public Loaded load() {
        FileHandle f = Gdx.files.external(FILE);
        if (!f.exists()) return new Loaded(null, false);
        try {
            return new Loaded(SaveMapper.fromJson(f.readString("UTF-8")), false);
        } catch (RuntimeException e) {
            Gdx.app.error("SaveManager", "저장을 읽지 못함", e);
            return new Loaded(null, true);
        }
    }

    public void delete() {
        Gdx.files.external(FILE).delete();
    }
}
