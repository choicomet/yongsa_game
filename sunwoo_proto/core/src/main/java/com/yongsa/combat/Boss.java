package com.yongsa.combat;

import com.badlogic.gdx.math.Rectangle;
import com.yongsa.config.MonsterDef;
import com.yongsa.world.Room;

/**
 * 보스: 기본 AI(쫓아가 공격)에 특수 공격(BossPattern)을 정해진 순서로 섞는다. 밀려나지 않는다.
 * <ul>
 *   <li>좀비킹 — RUSH: 방향선 예고 뒤 세 번 연달아 달려든다.</li>
 *   <li>리치 — 원거리 탄 + BONE_SPIKE(용사가 있던 자리마다 예고 뒤 가시) + SUMMON(해골 소환).</li>
 *   <li>마룡 — POISON_HOWL / LIGHTNING_HOWL / ICE_TAIL / FIRE_SPIN.</li>
 *   <li>마왕 — 1페이즈 SLAM·CHARGE·NOVA. 체력이 다 닳으면 무적 상태로 모습이 바뀌며
 *       1.5배 체력으로 다시 차고, 2페이즈는 TENTACLE이 더해지고 더 빠르다.</li>
 * </ul>
 * 그리기 쪽은 pose·warn*·spinAngle·phase만 읽는다.
 */
public class Boss extends Monster {

    /** 그림 자세. 렌더러가 프레임으로 바꾼다. */
    public enum Pose { IDLE, RUSH, CAST, HOWL_DOWN, HOWL_UP, TAIL_UP, SPIN }

    /** 보스 몸에 붙은 예고 표시 (바닥 위험 지대는 Hazard가 따로 그린다). */
    public enum Warn { NONE, CIRCLE, LINE }

    public static final float TRANSITION_TIME = 3.2f;
    private static final float SPIKE_GAP = 0.32f;

    private BossPattern[] patterns;
    private int nextPattern;
    private float patternTimer;
    private BossPattern current;
    private float patternTime;
    private int step;
    private float stepTime;
    private boolean chargeHit, charging;
    private float chargeDistance, spinDuration, fireTimer;

    public Pose pose = Pose.IDLE;
    public Warn warn = Warn.NONE;
    public float warnX, warnY, warnRadius, warnDirX, warnDirY, warnLength, warnProgress;
    /** 화염 회전 중 몸 회전 (도). */
    public float spinAngle;

    /** 1 또는 2. */
    public int phase = 1;
    private boolean transitioning;
    private float transitionTime;
    private boolean transitionStarted, transitionEnded;
    /** 최종 보스: 성검이 아니면 피해를 받지 않는다 (FR-던전-13). */
    public final boolean finalBoss;
    private final int baseAttack;

    public Boss(MonsterDef def, Room room, float x, float y, float hpMul, float atkMul, boolean finalBoss) {
        super(def, room, x, y, hpMul, atkMul);
        this.finalBoss = finalBoss;
        this.baseAttack = attack;
        patterns = parse(def.patterns);
        patternTimer = def.patternInterval * 0.5f;
    }

    private static BossPattern[] parse(String[] names) {
        BossPattern[] out = new BossPattern[names.length];
        for (int i = 0; i < names.length; i++) out[i] = BossPattern.valueOf(names[i]);
        return out;
    }

    @Override
    public boolean isBoss() {
        return true;
    }

    @Override
    public void knockback(float fromX, float fromY, float distance) {
        // 보스는 밀려나지 않는다
    }

    @Override
    public String sprite() {
        return phase == 2 && def.phase2Sprite != null ? def.phase2Sprite : def.sprite;
    }

    @Override
    protected float dyingTime() {
        return finalBoss ? 2.6f : 1.1f;
    }

    @Override
    public boolean canBeHit() {
        return super.canBeHit() && !transitioning;
    }

    public boolean transitioning() {
        return transitioning;
    }

    public float transitionProgress() {
        return transitioning ? Math.min(1f, transitionTime / TRANSITION_TIME) : 0f;
    }

    /** 2페이즈로 넘어가기 시작한 순간 한 번 true (대사용). */
    public boolean consumeTransitionStart() {
        boolean b = transitionStarted;
        transitionStarted = false;
        return b;
    }

    public boolean consumeTransitionEnd() {
        boolean b = transitionEnded;
        transitionEnded = false;
        return b;
    }

    public BossPattern currentPattern() {
        return current;
    }

    @Override
    public boolean damage(int amount, float fromX, float fromY, float knock) {
        if (!canBeHit()) return false;
        if (def.hasPhase2() && phase == 1 && amount >= hp) {
            hp = 1;
            flash = 0.2f;
            startTransition();
            return false;
        }
        return super.damage(amount, fromX, fromY, knock);
    }

    private void startTransition() {
        transitioning = true;
        transitionTime = 0;
        transitionStarted = true;
        endPatternQuiet();
        maxHp = Math.round(maxHp * def.phase2HpMultiplier);
    }

    private float speedMul() {
        return phase == 2 ? 1.2f : 1f;
    }

    // =================================================================== 한 프레임

    @Override
    public void update(float delta, CombatContext ctx) {
        if (transitioning) {
            animTime += delta;
            flash = Math.max(0, flash - delta);
            transitionTime += delta;
            // 체력이 아래에서부터 차오른다
            float fill = Math.min(1f, transitionTime / (TRANSITION_TIME * 0.75f));
            hp = Math.max(1, Math.round(maxHp * fill));
            if (transitionTime >= TRANSITION_TIME * 0.5f && phase == 1) {
                phase = 2;
                attack = Math.round(baseAttack * def.phase2AttackMultiplier);
                patterns = parse(def.phase2Patterns.length > 0 ? def.phase2Patterns : def.patterns);
                nextPattern = 0;
                ctx.shake(7, 0.8f);
                ctx.sound("boss_roar");
            }
            if (transitionTime >= TRANSITION_TIME) {
                transitioning = false;
                transitionEnded = true;
                hp = maxHp;
                patternTimer = 0;
                setState(State.CHASE);
            }
            return;
        }
        if (!alive() || state == State.SPAWNING || state == State.IDLE || current == null) {
            pose = Pose.IDLE;
            warn = Warn.NONE;
            super.update(delta * (state == State.CHASE ? speedMul() : 1f), ctx);
            if (alive() && patterns.length > 0 && (state == State.CHASE || state == State.RECOVER)) {
                patternTimer += delta;
                float interval = def.patternInterval * (phase == 2 ? 0.72f : 1f);
                if (patternTimer >= interval) {
                    patternTimer = 0;
                    startPattern(patterns[nextPattern], ctx);
                    nextPattern = (nextPattern + 1) % patterns.length;
                }
            }
            return;
        }
        animTime += delta;
        flash = Math.max(0, flash - delta);
        patternTime += delta;
        stepTime += delta;
        moving = false;
        runPattern(delta, ctx);
    }

    // =================================================================== 특수 공격

    private void startPattern(BossPattern p, CombatContext ctx) {
        HeroUnit hero = ctx.heroUnit();
        current = p;
        patternTime = 0;
        stepTime = 0;
        step = 0;
        charging = false;
        warn = Warn.NONE;
        pose = Pose.IDLE;
        switch (p) {
            case SLAM -> {
                warn = Warn.CIRCLE;
                warnX = hero.centerX();
                warnY = hero.centerY();
                warnRadius = def.slamRadius;
                pose = Pose.CAST;
            }
            case CHARGE, RUSH -> aimLine(hero, p == BossPattern.RUSH ? Pose.RUSH : Pose.CAST);
            case NOVA, SUMMON, BONE_SPIKE, TENTACLE -> {
                pose = Pose.CAST;
                ctx.sound("cast");
            }
            case POISON_HOWL -> {
                pose = Pose.HOWL_DOWN;
                ctx.sound("boss_roar");
            }
            case LIGHTNING_HOWL -> {
                pose = Pose.HOWL_UP;
                ctx.sound("boss_roar");
            }
            case ICE_TAIL -> {
                pose = Pose.TAIL_UP;
                ctx.sound("ice_charge");
            }
            case FIRE_SPIN -> {
                pose = Pose.SPIN;
                spinDuration = def.spinMin + ctx.random().nextFloat() * Math.max(0, def.spinMax - def.spinMin);
                fireTimer = 0;
                spinAngle = 0;
                ctx.sound("fire_spin");
            }
        }
    }

    private void aimLine(HeroUnit hero, Pose p) {
        float dx = hero.centerX() - centerX(), dy = hero.centerY() - centerY();
        float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
        warn = Warn.LINE;
        warnDirX = dx / len;
        warnDirY = dy / len;
        warnX = centerX();
        warnY = centerY();
        warnLength = Math.min(520, len + 120);
        faceLeft = warnDirX < 0;
        pose = p;
    }

    private void runPattern(float delta, CombatContext ctx) {
        HeroUnit hero = ctx.heroUnit();
        switch (current) {
            case SLAM -> {
                warnProgress = patternTime / def.slamWarning;
                if (patternTime >= def.slamWarning) {
                    Effect e = new Effect(Effect.Type.SLAM, warnX, warnY, 0.35f);
                    e.radius = def.slamRadius;
                    ctx.effect(e);
                    ctx.shake(6, 0.25f);
                    ctx.sound("slam");
                    float dx = hero.centerX() - warnX, dy = hero.centerY() - warnY;
                    if (dx * dx + dy * dy <= def.slamRadius * def.slamRadius) {
                        ctx.hitHeroArea(Math.round(attack * def.slamMultiplier), warnX, warnY);
                    }
                    endPattern();
                }
            }
            case CHARGE -> {
                if (!charging) {
                    warnProgress = patternTime / def.chargeWarning;
                    if (patternTime >= def.chargeWarning) beginCharge(ctx);
                } else if (chargeStep(delta, ctx, def.chargeSpeed * speedMul(), 1.3f, 460)) {
                    endPattern();
                }
            }
            case RUSH -> runRush(delta, ctx, hero);
            case NOVA -> {
                if (patternTime >= 0.7f) {
                    int n = phase == 2 ? Math.round(def.novaCount * 1.5f) : def.novaCount;
                    float offset = phase == 2 ? (float) (Math.PI / n) : 0;
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * i / n + offset;
                        float sp = def.projectileSpeed;
                        ctx.spawnProjectile(new Projectile(centerX(), centerY() + 20, (float) Math.cos(a) * sp,
                                (float) Math.sin(a) * sp, 10, Math.round(attack * 0.7f), def.projectile, 4f));
                    }
                    ctx.sound("nova");
                    endPattern();
                }
            }
            case BONE_SPIKE -> {
                // 용사가 "지금" 있는 자리에 하나씩. 예고가 뜬 자리는 움직이지 않는다
                while (step < def.spikeCount && patternTime >= 0.25f + step * SPIKE_GAP) {
                    ctx.addHazard(new Hazard(Hazard.Type.BONE_SPIKE, hero.footX(), hero.footY() + 2, def.spikeRadius,
                            0, def.spikeWarning, 0, Math.round(attack * def.spikeMultiplier)));
                    step++;
                }
                if (step >= def.spikeCount && patternTime >= 0.25f + def.spikeCount * SPIKE_GAP + 0.2f) endPattern();
            }
            case SUMMON -> {
                if (step == 0 && patternTime >= 0.8f) {
                    ctx.summon(this, def.summonId, def.summonCount, def.summonMax);
                    step = 1;
                }
                if (patternTime >= 1.1f) endPattern();
            }
            case POISON_HOWL -> {
                if (step == 0 && patternTime >= 0.7f) {
                    step = 1;
                    Rectangle area = ctx.arenaOf(this);
                    for (int i = 0; i < def.cloudCount; i++) {
                        float x, y;
                        if (i == 0) {
                            x = hero.footX();
                            y = hero.footY();
                        } else {
                            float[] p = randomPoint(ctx, area, def.cloudRadius + 24);
                            x = p[0];
                            y = p[1];
                        }
                        ctx.addHazard(new Hazard(Hazard.Type.POISON_CLOUD, x, y, def.cloudRadius, i * 0.15f, 0.6f,
                                def.cloudLife, 0));
                    }
                    ctx.sound("poison");
                }
                if (patternTime >= 1.3f) endPattern();
            }
            case LIGHTNING_HOWL -> {
                float start = 0.7f;
                while (step < def.strikeCount && patternTime >= start + step * def.strikeInterval) {
                    float x = hero.footX(), y = hero.footY();
                    if (step % 2 == 1) {
                        // 홀수 번째는 근처 아무 데나: 한 방향으로만 도망치지 못하게
                        double a = ctx.random().nextFloat() * Math.PI * 2;
                        float r = 50 + ctx.random().nextFloat() * 90;
                        x += (float) Math.cos(a) * r;
                        y += (float) Math.sin(a) * r;
                    }
                    ctx.addHazard(new Hazard(Hazard.Type.LIGHTNING, x, y, def.strikeRadius, 0, def.strikeWarning, 0,
                            Math.round(attack * def.strikeMultiplier)));
                    step++;
                }
                if (step >= def.strikeCount && patternTime >= start + def.strikeCount * def.strikeInterval + 0.3f) {
                    endPattern();
                }
            }
            case ICE_TAIL -> {
                if (step == 0 && patternTime >= 0.85f) {
                    step = 1;
                    float tx = tailX(), ty = tailY();
                    float aim = (float) Math.atan2(hero.centerY() - ty, hero.centerX() - tx);
                    for (int i = 0; i < def.iceCount; i++) {
                        double a;
                        if (i < 3) a = aim + (i - 1) * 0.18f; // 세 개는 용사 쪽으로
                        else a = Math.PI * 2 * i / def.iceCount + ctx.random().nextFloat() * 0.3f;
                        float sp = def.iceSpeed * (0.8f + ctx.random().nextFloat() * 0.4f);
                        ctx.spawnProjectile(new Projectile(tx, ty, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                                9, Math.round(attack * def.iceMultiplier), "ice_chunk", 3.2f)
                                .with(Projectile.Status.FREEZE));
                    }
                    ctx.shake(4, 0.3f);
                    ctx.sound("ice_burst");
                }
                if (patternTime >= 1.3f) endPattern();
            }
            case FIRE_SPIN -> {
                spinAngle += delta * 300f;
                fireTimer += delta;
                while (fireTimer >= def.fireRate) {
                    fireTimer -= def.fireRate;
                    double base = Math.toRadians(-spinAngle * 0.5f);
                    for (int k = 0; k < 3; k++) {
                        double a = base + Math.PI * 2 * k / 3;
                        float sp = def.fireSpeed;
                        ctx.spawnProjectile(new Projectile(centerX() + (float) Math.cos(a) * 30,
                                centerY() + 24 + (float) Math.sin(a) * 20, (float) Math.cos(a) * sp,
                                (float) Math.sin(a) * sp, 9, Math.round(attack * def.fireMultiplier), "fire_bolt", 3.5f)
                                .with(Projectile.Status.BURN));
                    }
                }
                if (patternTime >= spinDuration) {
                    spinAngle = 0;
                    endPattern();
                }
            }
            case TENTACLE -> {
                if (step == 0 && patternTime >= 0.45f) {
                    step = 1;
                    for (int i = 0; i < def.tentacleCount; i++) {
                        float x = hero.footX(), y = hero.footY();
                        if (i > 0) {
                            double a = Math.PI * 2 * i / (def.tentacleCount - 1) + ctx.random().nextFloat() * 0.5f;
                            float r = 60 + ctx.random().nextFloat() * 110;
                            x += (float) Math.cos(a) * r;
                            y += (float) Math.sin(a) * r;
                        }
                        ctx.addHazard(new Hazard(Hazard.Type.TENTACLE, x, y, def.tentacleRadius, i * 0.07f,
                                def.tentacleWarning, 0, Math.round(attack * def.tentacleMultiplier)));
                    }
                }
                if (patternTime >= 0.9f) endPattern();
            }
        }
    }

    /** 좀비킹: 예고(방향선) → 돌진 → 잠깐 숨 고르기, rushCount번. 예고 방향은 매번 새로 잡는다. */
    private void runRush(float delta, CombatContext ctx, HeroUnit hero) {
        if (!charging) {
            if (stepTime < 0) return; // 숨 고르는 중
            if (warn == Warn.NONE) {
                aimLine(hero, Pose.RUSH);
                stepTime = 0;
            }
            warnProgress = stepTime / def.rushWarning;
            if (stepTime >= def.rushWarning) beginCharge(ctx);
            return;
        }
        if (chargeStep(delta, ctx, def.rushSpeed * speedMul(), def.rushMultiplier, 520)) {
            step++;
            charging = false;
            warn = Warn.NONE;
            if (step >= def.rushCount) {
                endPattern();
                return;
            }
            stepTime = -0.3f;
        }
    }

    private void beginCharge(CombatContext ctx) {
        charging = true;
        chargeDistance = 0;
        chargeHit = false;
        stepTime = 0;
        warn = Warn.NONE;
        ctx.sound("charge");
    }

    /** 돌진 한 프레임. 벽에 닿거나 최대 거리를 넘으면 true. */
    private boolean chargeStep(float delta, CombatContext ctx, float speed, float mult, float maxDistance) {
        HeroUnit hero = ctx.heroUnit();
        moving = true;
        float step = speed * delta;
        boolean wall = ctx.collision().move(body, warnDirX * step, warnDirY * step);
        chargeDistance += step;
        if (!chargeHit && MonsterAI.touching(this, hero, 6)) {
            chargeHit = true;
            ctx.hitHeroHeavy(Math.round(attack * mult), centerX(), centerY());
        }
        if (wall) ctx.shake(4, 0.2f);
        return wall || chargeDistance > maxDistance;
    }

    private float[] randomPoint(CombatContext ctx, Rectangle area, float margin) {
        HeroUnit hero = ctx.heroUnit();
        float x = area.x + area.width / 2, y = area.y + area.height / 2;
        for (int tries = 0; tries < 20; tries++) {
            x = area.x + margin + ctx.random().nextFloat() * Math.max(1, area.width - margin * 2);
            y = area.y + margin + ctx.random().nextFloat() * Math.max(1, area.height - margin * 2);
            // 용사 바로 옆은 피해서 (첫 구름이 이미 용사 자리에 있다)
            if (Math.hypot(x - hero.footX(), y - hero.footY()) > def.cloudRadius * 2.2f) break;
        }
        return new float[]{x, y};
    }

    /** 꼬리 끝 (얼음 덩이가 나오는 곳): 몸 뒤쪽 위. */
    public float tailX() {
        return centerX() + (faceLeft ? 46 : -46);
    }

    public float tailY() {
        return centerY() + 40;
    }

    private void endPattern() {
        endPatternQuiet();
        setState(State.RECOVER);
    }

    private void endPatternQuiet() {
        current = null;
        patternTime = 0;
        charging = false;
        warn = Warn.NONE;
        pose = Pose.IDLE;
        spinAngle = 0;
    }

    /** 사망·재도전 때 초기 상태로. */
    public void resetBoss() {
        resetHp();
        endPatternQuiet();
        phase = 1;
        transitioning = false;
        setState(State.IDLE);
    }
}
