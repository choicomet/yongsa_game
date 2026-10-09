package com.yongsa.model;

/** 광물방에 생성된 광맥 하나. 후보 ID는 Tiled 맵의 veins 오브젝트 이름이다. */
public class VeinState {
    public String candidateId;
    public boolean mined;

    public VeinState() {}

    public VeinState(String candidateId) {
        this.candidateId = candidateId;
    }
}
