package com.yongsa.model;

import java.util.ArrayList;

/** 현재 채굴 회차에서 한 광물방에 생성된 광맥 목록. */
public class MineRoomState {
    public String roomId;
    public ArrayList<VeinState> veins = new ArrayList<>();

    public MineRoomState() {}

    public MineRoomState(String roomId) {
        this.roomId = roomId;
    }

    public VeinState vein(String candidateId) {
        for (VeinState v : veins) if (v.candidateId.equals(candidateId)) return v;
        return null;
    }
}
