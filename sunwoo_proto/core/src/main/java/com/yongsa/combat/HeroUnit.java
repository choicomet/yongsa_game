package com.yongsa.combat;

import com.yongsa.config.HeroDef;
import com.yongsa.config.SkillDefs;
import com.yongsa.world.CollisionWorld;

/**
 * 용사의 몸: 8방향 이동, 바라보는 방향, 회피·돌진, 쿨타임, 무적 (FR-맵-01, FR-전투-02~08).
 * 체력·흡수한 힘은 model.Hero가 가진다.
 */
public class HeroUnit extends Body {

    public enum Facing { DOWN, UP, LEFT, RIGHT }

    public static final float W = 14, H = 8;

    private final HeroDef def;
    private final SkillDefs skills;

    /** 바라보는 방향 (8방향, 정규화). 공격 방향이 된다. */
    public float dirX = 0, dirY = -1;
    public Facing facing = Facing.DOWN;

    public float attackCooldown, whirlCooldown, dashCooldown, dodgeCooldown;
    private float invincible;

    /** 회피·돌진 중이면 남은 시간과 속도. */
    private float dodgeTime, dashTime, forcedVx, forcedVy;
    /** 공격 연출: 남은 시간과 그때의 범위 (공격할 때만 그린다). */
    public float swingTime, swingDuration, swingRange, swingArc, swingDirX, swingDirY;
    public Attack.Kind swingKind = Attack.Kind.BASIC;
    public float mineTime;

    public HeroUnit(HeroDef def, SkillDefs skills, float x, float y) {
        super(x, y, W, H);
        this.def = def;
        this.skills = skills;
    }

    public void update(float delta, float inX, float inY, CollisionWorld col) {
        animTime += delta;
        attackCooldown = Math.max(0, attackCooldown - delta);
        whirlCooldown = Math.max(0, whirlCooldown - delta);
        dashCooldown = Math.max(0, dashCooldown - delta);
        dodgeCooldown = Math.max(0, dodgeCooldown - delta);
        invincible = Math.max(0, invincible - delta);
        flash = Math.max(0, flash - delta);
        swingTime = Math.max(0, swingTime - delta);
        mineTime = Math.max(0, mineTime - delta);

        if (dodgeTime > 0 || dashTime > 0) {
            dodgeTime = Math.max(0, dodgeTime - delta);
            dashTime = Math.max(0, dashTime - delta);
            moving = true;
            col.move(body, forcedVx * delta, forcedVy * delta);
            return;
        }

        float len = (float) Math.sqrt(inX * inX + inY * inY);
        moving = len > 0.01f;
        if (moving) {
            // 대각선도 직선과 같은 속도 (FR-맵-01)
            inX /= len;
            inY /= len;
            dirX = inX;
            dirY = inY;
            updateFacing();
            float speed = def.speed * (swingTime > 0 ? 0.6f : 1f);
            col.move(body, inX * speed * delta, inY * speed * delta);
        }
        updateKnockback(delta, col);
    }

    private void updateFacing() {
        if (Math.abs(dirX) > Math.abs(dirY) + 0.01f) facing = dirX < 0 ? Facing.LEFT : Facing.RIGHT;
        else facing = dirY > 0 ? Facing.UP : Facing.DOWN;
        if (Math.abs(dirX) > 0.01f) faceLeft = dirX < 0;
    }

    /** 회피·돌진 중이 아니면 행동할 수 있다. */
    public boolean free() {
        return dodgeTime <= 0 && dashTime <= 0;
    }

    public boolean dashing() {
        return dashTime > 0;
    }

    public boolean dodging() {
        return dodgeTime > 0;
    }

    public void startSwing(Attack.Kind kind, float duration, float range, float arc) {
        swingKind = kind;
        swingTime = duration;
        swingDuration = duration;
        swingRange = range;
        swingArc = arc;
        swingDirX = dirX;
        swingDirY = dirY;
    }

    /** 회피: 바라보는(또는 누르는) 방향으로 짧게 구르며 무적 (Shift). */
    public boolean dodge(float inX, float inY) {
        if (!free() || dodgeCooldown > 0) return false;
        float len = (float) Math.sqrt(inX * inX + inY * inY);
        float dx = len > 0.01f ? inX / len : dirX, dy = len > 0.01f ? inY / len : dirY;
        dirX = dx;
        dirY = dy;
        updateFacing();
        dodgeTime = skills.dodge.duration;
        forcedVx = dx * skills.dodge.distance / skills.dodge.duration;
        forcedVy = dy * skills.dodge.distance / skills.dodge.duration;
        invincible = Math.max(invincible, skills.dodge.duration + 0.05f);
        dodgeCooldown = skills.dodge.cooldown;
        stopKnockback();
        return true;
    }

    /** 돌진 베기: 0.2초 동안 앞으로 돌진하며 무적 (L). 벽은 통과하지 못한다. */
    public boolean dash() {
        if (!free() || dashCooldown > 0) return false;
        dashTime = skills.dash.duration;
        forcedVx = dirX * skills.dash.distance / skills.dash.duration;
        forcedVy = dirY * skills.dash.distance / skills.dash.duration;
        invincible = Math.max(invincible, skills.dash.duration);
        dashCooldown = skills.dash.cooldown;
        stopKnockback();
        return true;
    }

    public boolean invincible() {
        return invincible > 0;
    }

    /** 맞았을 때: 무적·깜빡임·밀려남. */
    public void onHit(float fromX, float fromY) {
        invincible = def.invincibleTime;
        flash = 0.15f;
        knockback(fromX, fromY, def.knockback);
    }

    public void resetTimers() {
        attackCooldown = whirlCooldown = dashCooldown = dodgeCooldown = 0;
        invincible = dodgeTime = dashTime = swingTime = 0;
        stopKnockback();
    }

    public void lookAt(float dx, float dy) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        dirX = dx / len;
        dirY = dy / len;
        updateFacing();
    }
}
