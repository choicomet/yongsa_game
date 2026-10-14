package com.yongsa.gfx;

import com.badlogic.gdx.math.MathUtils;
import com.yongsa.combat.AttackInstance;

/** Shared timing for shoulder, blade and its tapered trail. Angles are relative to aim. */
public final class AttackMotion {
    private AttackMotion() {}
    private static float smooth(float p) { p=MathUtils.clamp(p,0,1); return p*p*(3-2*p); }
    /** 내려찍기에서 무기가 지나가는 쪽: 항상 화면 위쪽으로 넘어가야 머리 위로 드는 것처럼 보인다. */
    public static float slamSide(AttackInstance a) { return com.yongsa.combat.Dir8.DX[a.dir]>=0?1:-1; }
    /** 내려찍기: 준비 동안 등 뒤까지 들어 올리고, 판정 순간 조준 방향으로 단숨에 내려온다. */
    private static float slamAngle(AttackInstance a, float time) {
        float sign=slamSide(a);
        if(time<a.windup) return sign*(40+120*smooth(time/a.windup));
        if(time<a.windup+a.active) {
            float p=MathUtils.clamp((time-a.windup)/(a.active*0.55f),0,1);
            return sign*160*(1-p)*(1-p)*(1-p);
        }
        return sign*30*smooth((time-a.windup-a.active)/a.recovery);
    }
    /** 내려찍기에서 무기를 치켜든 높이(px). 가장 높이 든 순간에 최대. */
    public static float lift(AttackInstance a) {
        if(!a.slam) return 0;
        if(a.t<a.windup) return 16*smooth(a.t/a.windup);
        if(a.t<a.windup+a.active) return 16*(1-MathUtils.clamp((a.t-a.windup)/(a.active*0.55f),0,1));
        return 0;
    }
    public static float angle(AttackInstance a, float time) {
        if(a.slam) return slamAngle(a,time);
        float sign=a.combo%2==0?-1:1;
        float back=a.finisher?105:80;
        if(time<a.windup) return sign*(30+(back-30)*smooth(time/a.windup));
        if(time<a.windup+a.active) {
            float p=MathUtils.clamp((time-a.windup)/a.active,0,1);
            return sign*(back-(back+75)*(1-(1-p)*(1-p)*(1-p)));
        }
        return sign*(-75+105*smooth((time-a.windup-a.active)/a.recovery));
    }
    public static float lean(AttackInstance a) {
        if(a.t<a.windup)return -3*smooth(a.t/a.windup);
        if(a.t<a.windup+a.active) {
            float p=(a.t-a.windup)/a.active;
            return (a.finisher?10:6)*(float)Math.sin(p*Math.PI);
        }
        return 0;
    }
    public static float progress(AttackInstance a) { return MathUtils.clamp((a.t-a.windup)/a.active,0,1); }
}
