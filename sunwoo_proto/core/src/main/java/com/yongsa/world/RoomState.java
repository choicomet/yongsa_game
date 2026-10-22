package com.yongsa.world;

/** 처음 → 전투 중(문 잠김) → 비움. 침대를 쓰면 처음으로 (보스방은 보스를 잡았으면 그대로). */
public enum RoomState { IDLE, FIGHTING, CLEARED }
