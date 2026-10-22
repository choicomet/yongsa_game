package com.yongsa.combat;

import com.yongsa.config.MonsterDef;
import com.yongsa.world.Room;

/**
 * 보스: 기본 AI에 특수 공격(BossPattern)을 번갈아 섞는다. 밀려나지 않는다.
 * 마왕은 체력이 절반 아래로 내려가면 2페이즈(더 빠르고 자주 공격)가 된다.
 */
public class Boss extends Monster {

    public enum Pattern { NONE, SLAM_WARN, CHARGE_WARN, CHARGING, NOVA_CHARGE }

    private final BossPattern[] patterns;
    private int nextPattern;
    private float patternTimer;
    public Pattern pattern = Pattern.NONE;
    public float patternTime;
    /** 내려칠 자리 / 돌진 방향. */
    public float targetX, targetY, chargeDirX, chargeDirY;
    private float chargeDistance;
    private boolean chargeHit;
    public boolean phase2;
    /** 최종 보스: 깨어난 성검이 아니면 피해를 받지 않는다 (FR-던전-13). */
    public final boolean finalBoss;

    public Boss(MonsterDef def, Room room, float x, float y, float hpMul, float atkMul, boolean finalBoss) {
        super(def, room, x, y, hpMul, atkMul);
        this.finalBoss = finalBoss;
        patterns = new BossPattern[def.patterns.length];
        for (int i = 0; i < patterns.length; i++) patterns[i] = BossPattern.valueOf(def.patterns[i]);
        patternTimer = def.patternInterval * 0.6f;
    }

    @Override
    public boolean isBoss() {
        return true;
    }

    @Override
    public void knockback(float fromX, float fromY, float distance) {
        // 보스는 밀려나지 않는다
    }

    private float speedMul() {
        return phase2 ? 1.25f : 1f;
    }

    @Override
    public void update(float delta, CombatContext ctx) {
        if (def.phase2Ratio > 0 && !phase2 && alive() && hp <= maxHp * def.phase2Ratio) {
            phase2 = true;
            ctx.shake(5, 0.5f);
            ctx.sound("boss_roar");
        }
        if (!alive() || state == State.SPAWNING || state == State.IDLE || pattern == Pattern.NONE) {
            super.update(delta * (state == State.CHASE ? speedMul() : 1f), ctx);
            if (alive() && state != State.SPAWNING && state != State.IDLE && patterns.length > 0
                    && (state == State.CHASE || state == State.RECOVER)) {
                patternTimer += delta;
                float interval = def.patternInterval * (phase2 ? 0.7f : 1f);
                if (patternTimer >= interval) {
                    patternTimer = 0;
                    startPattern(patterns[nextPattern], ctx);
                    nextPattern = (nextPattern + 1) % patterns.length;
                }
            }
            return;
        }
        // 특수 공격 중
        animTime += delta;
        flash = Math.max(0, flash - delta);
        patternTime += delta;
        HeroUnit hero = ctx.heroUnit();
        switch (pattern) {
            case SLAM_WARN -> {
                if (patternTime >= def.slamWarning) {
                    ctx.effect(slamEffect());
                    ctx.shake(6, 0.25f);
                    ctx.sound("slam");
                    float dx = hero.centerX() - targetX, dy = hero.centerY() - targetY;
                    if (dx * dx + dy * dy <= def.slamRadius * def.slamRadius) {
                        ctx.hitHero(Math.round(attack * def.slamMultiplier), targetX, targetY);
                    }
                    endPattern();
                }
            }
            case CHARGE_WARN -> {
                faceLeft = chargeDirX < 0;
                if (patternTime >= def.chargeWarning) {
                    pattern = Pattern.CHARGING;
                    patternTime = 0;
                    chargeDistance = 0;
                    chargeHit = false;
                    ctx.sound("charge");
                }
            }
            case CHARGING -> {
                moving = true;
                float step = def.chargeSpeed * speedMul() * delta;
                boolean wall = ctx.collision().move(body, chargeDirX * step, chargeDirY * step);
                chargeDistance += step;
                if (!chargeHit && MonsterAI.touching(this, hero, 6)) {
                    chargeHit = true;
                    ctx.hitHero(Math.round(attack * 1.3f), centerX(), centerY());
                }
                if (wall || chargeDistance > 460) {
                    if (wall) ctx.shake(4, 0.2f);
                    endPattern();
                }
            }
            case NOVA_CHARGE -> {
                if (patternTime >= 0.7f) {
                    int n = def.novaCount * (phase2 ? 2 : 1) / (phase2 ? 1 : 1);
                    float offset = phase2 ? (float) (Math.PI / n) : 0;
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * i / n + offset;
                        float sp = def.projectileSpeed;
                        ctx.spawnProjectile(new Projectile(centerX(), centerY() + 20, (float) Math.cos(a) * sp,
                                (float) Math.sin(a) * sp, 10, Math.round(attack * 0.7f),
                                finalBoss ? "bolt_purple" : "bolt_red", 4f));
                    }
                    ctx.sound("nova");
                    endPattern();
                }
            }
            default -> endPattern();
        }
    }

    private Effect slamEffect() {
        Effect e = new Effect(Effect.Type.SLAM, targetX, targetY, 0.35f);
        e.radius = def.slamRadius;
        return e;
    }

    private void startPattern(BossPattern p, CombatContext ctx) {
        HeroUnit hero = ctx.heroUnit();
        patternTime = 0;
        switch (p) {
            case SLAM -> {
                pattern = Pattern.SLAM_WARN;
                targetX = hero.centerX();
                targetY = hero.centerY();
            }
            case CHARGE -> {
                pattern = Pattern.CHARGE_WARN;
                float dx = hero.centerX() - centerX(), dy = hero.centerY() - centerY();
                float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
                chargeDirX = dx / len;
                chargeDirY = dy / len;
            }
            case NOVA -> pattern = Pattern.NOVA_CHARGE;
        }
    }

    private void endPattern() {
        pattern = Pattern.NONE;
        patternTime = 0;
        setState(State.RECOVER);
    }

    /** 사망·재도전 때 초기 상태로. */
    public void resetBoss() {
        resetHp();
        pattern = Pattern.NONE;
        phase2 = false;
        setState(State.IDLE);
    }
}
