package com.yongsa.combat;

import com.yongsa.config.GuardDef;
import com.yongsa.config.HeroDef;
import com.yongsa.config.SkillDefs;
import com.yongsa.world.CollisionWorld;

/**
 * 용사의 몸: 8방향 이동, 바라보는 방향, 회피·돌진, 막기, 상태이상, 쿨타임, 무적 (FR-맵-01, FR-전투-02~08).
 * 체력·흡수한 힘은 model.Hero가 가진다.
 */
public class HeroUnit extends Body {

    public enum Facing { DOWN, UP, LEFT, RIGHT }

    public static final float W = 14, H = 8;

    private final HeroDef def;
    private final SkillDefs skills;
    private final GuardDef guardDef;

    /** 막기 게이지(초), 막기 중인지, 깨진 뒤 남은 시간, 손 뗀 뒤 지난 시간. */
    public float guardMeter;
    public boolean guarding;
    public float guardBroken;
    private float guardIdle;
    /** 막은 순간 연출. */
    public float blockFlash;

    /** 상태이상 남은 시간. */
    public float poison, burn, shock, freeze;
    private float freezeImmune;
    /** 둔화(늪)·실명(리치)·기절(마왕 눈알) 남은 시간. */
    public float slow, blind, stun;
    private float slowRatio;
    /** 늪 위에 계속 서 있은 시간 / 둔화가 늪 위에서 풀렸는지 (그러면 2초를 버텨야 다시 걸린다). */
    private float swampHold;
    private boolean swampNeedsHold, inSwamp;

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
    /** 바람기둥에 맞아 날아가는 중 (빙글빙글 구르며 날아간다). 방향: 1 오른쪽, -1 왼쪽. */
    public float tumble, tumbleDir = 1;
    public static final float TUMBLE_TIME = 0.85f;

    public HeroUnit(HeroDef def, SkillDefs skills, GuardDef guard, float x, float y) {
        super(x, y, W, H);
        this.def = def;
        this.skills = skills;
        this.guardDef = guard;
        this.guardMeter = guard.max;
    }

    public void update(float delta, float inX, float inY, boolean guardHeld, CollisionWorld col) {
        animTime += delta;
        attackCooldown = Math.max(0, attackCooldown - delta);
        whirlCooldown = Math.max(0, whirlCooldown - delta);
        dashCooldown = Math.max(0, dashCooldown - delta);
        dodgeCooldown = Math.max(0, dodgeCooldown - delta);
        invincible = Math.max(0, invincible - delta);
        flash = Math.max(0, flash - delta);
        swingTime = Math.max(0, swingTime - delta);
        mineTime = Math.max(0, mineTime - delta);
        tumble = Math.max(0, tumble - delta);
        blockFlash = Math.max(0, blockFlash - delta);
        poison = Math.max(0, poison - delta);
        burn = Math.max(0, burn - delta);
        shock = Math.max(0, shock - delta);
        freezeImmune = Math.max(0, freezeImmune - delta);
        blind = Math.max(0, blind - delta);
        stun = Math.max(0, stun - delta);
        if (slow > 0) {
            slow = Math.max(0, slow - delta);
            // 늪 위에서 풀렸으면 바로 다시 걸리지 않는다
            if (slow == 0 && inSwamp) swampNeedsHold = true;
        }
        if (freeze > 0) {
            freeze = Math.max(0, freeze - delta);
            if (freeze == 0) freezeImmune = 1.0f;
        }

        updateGuard(delta, guardHeld && free() && freeze <= 0);

        if (freeze > 0 || stun > 0) {
            moving = false;
            updateKnockback(delta, col);
            return;
        }

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
            // 막는 동안에는 바라보는 방향을 유지한 채 천천히 움직인다
            if (!guarding) {
                dirX = inX;
                dirY = inY;
                updateFacing();
            }
            float speed = def.speed * (swingTime > 0 ? 0.6f : 1f) * (guarding ? guardDef.moveMultiplier : 1f)
                    * (slow > 0 ? 1f - slowRatio : 1f);
            col.move(body, inX * speed * delta, inY * speed * delta);
        }
        updateKnockback(delta, col);
    }

    private void updateFacing() {
        if (Math.abs(dirX) > Math.abs(dirY) + 0.01f) facing = dirX < 0 ? Facing.LEFT : Facing.RIGHT;
        else facing = dirY > 0 ? Facing.UP : Facing.DOWN;
        if (Math.abs(dirX) > 0.01f) faceLeft = dirX < 0;
    }

    // ------------------------------------------------------------------ 막기

    private void updateGuard(float delta, boolean held) {
        if (guardBroken > 0) {
            guardBroken = Math.max(0, guardBroken - delta);
            guarding = false;
            guardIdle = 0;
            return;
        }
        if (held && guardMeter > 0) {
            guarding = true;
            guardIdle = 0;
            guardMeter -= delta;
            if (guardMeter <= 0) breakGuard();
            return;
        }
        guarding = false;
        guardIdle += delta;
        if (guardIdle >= guardDef.regenDelay) guardMeter = Math.min(guardDef.max, guardMeter + guardDef.regenPerSecond * delta);
    }

    private void breakGuard() {
        guardMeter = 0;
        guarding = false;
        guardBroken = guardDef.breakTime;
    }

    /** (fromX, fromY)에서 온 공격을 지금 막을 수 있는지: 막기 중이고 앞쪽에서 왔을 때. */
    public boolean canBlock(float fromX, float fromY) {
        if (!guarding) return false;
        float dx = fromX - centerX(), dy = fromY - centerY();
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return true;
        float cos = (dx * dirX + dy * dirY) / len;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos)))) <= guardDef.frontArcDegrees / 2f;
    }

    /**
     * 막았다: 게이지가 닳고 살짝 밀린다. 게이지가 바닥나면 막기가 깨진다. 깨졌으면 true.
     * bossHit이면 스치기만 해도 한 번에 깨진다 (피해는 막는다).
     */
    public boolean onBlocked(float fromX, float fromY, boolean heavy, boolean bossHit) {
        if (bossHit) {
            blockFlash = 0.18f;
            knockback(fromX, fromY, 14);
            breakGuard();
            return true;
        }
        guardMeter -= heavy ? guardDef.heavyHitCost : guardDef.hitCost;
        blockFlash = 0.18f;
        knockback(fromX, fromY, heavy ? 12 : 6);
        if (guardMeter <= 0) {
            breakGuard();
            return true;
        }
        return false;
    }

    public float guardRatio() {
        return guardMeter / guardDef.max;
    }

    // ------------------------------------------------------------------ 상태이상

    public void applyPoison(float t) { poison = Math.max(poison, t); }
    public void applyBurn(float t) { burn = t; }
    public void applyShock(float t) { shock = t; }

    /** 빙결은 걸려 있는 동안·풀린 직후 잠깐은 다시 걸리지 않는다. */
    public boolean applyFreeze(float t) {
        if (freeze > 0 || freezeImmune > 0) return false;
        freeze = t;
        guarding = false;
        dodgeTime = dashTime = 0;
        stopKnockback();
        return true;
    }

    public boolean frozen() { return freeze > 0; }

    /** 기절: 빙결처럼 아무것도 못 한다. */
    public void applyStun(float t) {
        stun = Math.max(stun, t);
        guarding = false;
        dodgeTime = dashTime = 0;
    }

    public boolean stunned() { return stun > 0; }

    /** 실명: 시간을 다시 채운다 (겹쳐서 늘어나지 않음). */
    public void applyBlind(float t) { blind = t; }
    public boolean blinded() { return blind > 0; }
    public boolean slowed() { return slow > 0; }

    /**
     * 늪 판정 (매 프레임). 둔화 규칙:
     * 걸려 있는 동안 다시 밟아도 늘어나지 않는다. 늪 위에서 풀렸으면 retouch초 동안 계속 서 있어야 다시 걸린다.
     * 늪 밖으로 나갔다가 다시 들어오면 바로 걸린다. 걸렸으면 true.
     */
    public boolean updateSwamp(boolean onSwamp, float delta, float duration, float ratio, float retouch) {
        inSwamp = onSwamp;
        if (!onSwamp) {
            swampHold = 0;
            swampNeedsHold = false;
            return false;
        }
        if (slow > 0) {
            swampHold = 0;
            return false;
        }
        if (swampNeedsHold) {
            swampHold += delta;
            if (swampHold < retouch) return false;
        }
        slow = duration;
        slowRatio = ratio;
        swampHold = 0;
        swampNeedsHold = false;
        return true;
    }
    /** 감전: Q·L 스킬을 못 쓴다. */
    public boolean skillsSealed() { return shock > 0; }

    public void clearStatuses() {
        poison = burn = shock = freeze = freezeImmune = slow = blind = stun = swampHold = 0;
        swampNeedsHold = inSwamp = false;
    }

    /** 회피·돌진·빙결·기절·날아가는 중이 아니면 행동할 수 있다. */
    public boolean free() {
        return dodgeTime <= 0 && dashTime <= 0 && freeze <= 0 && stun <= 0 && tumble <= 0;
    }

    /** 바람에 맞아 날아가기 시작 (vx: 날아가는 쪽 가로 속도). */
    public void startTumble(float vx) {
        tumble = TUMBLE_TIME;
        tumbleDir = vx >= 0 ? 1 : -1;
    }

    public boolean dashing() {
        return dashTime > 0;
    }

    public boolean dodging() {
        return dodgeTime > 0;
    }

    /** 구르기 진행 (0 → 1). 구르는 그림을 고를 때 쓴다. */
    public float dodgeProgress() {
        float d = skills.dodge.duration;
        return d <= 0 ? 1f : Math.min(1f, Math.max(0f, 1f - dodgeTime / d));
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

    /** 회피: 바라보는(또는 누르는) 방향으로 짧게 구르며 무적 (Space). */
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

    /** 테스트용: 무적 시간을 정한다. */
    public void invincibleForTest(float t) {
        invincible = t;
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
        invincible = dodgeTime = dashTime = swingTime = tumble = 0;
        guarding = false;
        guardBroken = 0;
        guardMeter = guardDef.max;
        clearStatuses();
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
