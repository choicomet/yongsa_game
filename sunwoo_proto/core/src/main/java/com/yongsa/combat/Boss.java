package com.yongsa.combat;

import com.badlogic.gdx.math.Rectangle;
import com.yongsa.config.MonsterDef;
import com.yongsa.world.Room;

/**
 * 보스: 기본 AI(쫓아가 공격)에 특수 공격(BossPattern)을 정해진 순서로 섞는다. 밀려나지 않는다.
 * <ul>
 *   <li>좀비킹 — RUSH: 방향선 예고 뒤 세 번 연달아 달려든다.</li>
 *   <li>리치 — 원거리 탄 + BONE_ARMOR(방어막) + BONE_SPIKE(용사가 있던 자리마다 예고 뒤 가시) +
 *       SUMMON(해골·작은 리치) + SWAMP(넓어지는 늪 → 뼈창). 가시·소환 때 실명. 구석에 몰리면 순간이동.</li>
 *   <li>마룡 — POISON_HOWL / LIGHTNING_HOWL / ICE_TAIL / FIRE_SPIN.</li>
 *   <li>마왕 — 1페이즈 SLAM·CHARGE·NOVA. 체력이 다 닳으면 무적 상태로 모습이 바뀌며
 *       1.5배 체력으로 다시 차고, 2페이즈는 TENTACLE이 더해지고 더 빠르다.</li>
 * </ul>
 * 보스는 용사의 공격·회피로 밀리지 않는다 (GameSession.separate가 용사 쪽을 밀어낸다).
 * 그리기 쪽은 pose·warn*·spinAngle·phase·armor·stagger만 읽는다.
 */
public class Boss extends Monster {

    /** 그림 자세. 렌더러가 프레임으로 바꾼다. */
    public enum Pose { IDLE, RUSH, CAST, HOWL_DOWN, HOWL_UP, TAIL_UP, SPIN, BACK }

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
    /**
     * 화염 구슬 회전 중 몸이 바라보는 방향 (도, 0 = 화면 아래·정면, 90 = 오른쪽, 180 = 뒤). 렌더러가 앞·옆·뒷모습을 고른다.
     */
    public float spinAngle;

    /** 1 또는 2. */
    public int phase = 1;
    private boolean transitioning;
    private float transitionTime;
    private boolean transitionStarted, transitionEnded;
    /** 최종 보스: 성검이 아니면 피해를 받지 않는다 (FR-던전-13). */
    public final boolean finalBoss;
    private final int baseAttack;

    /** 뼈갑옷 방어막 (남아 있으면 체력이 닳지 않는다). */
    public int armor, armorMax;
    /** 방어막이 깨진 뒤 휘청이는 남은 시간. */
    public float stagger;
    private boolean armorBroken, blinked;
    private float blindCooldown, blinkCooldown, closeTime;

    // ---- 마룡: 광폭화 (바람의 고치)
    public boolean enraged, cocoon;
    public float cocoonTime;
    private float meteorTimer;
    private boolean cocoonStarted, cocoonEnded;
    /** 순간이동 연출: 1이면 다 사라짐. */
    public float vanish;

    // ---- 마왕 1: 척추 채찍
    public boolean whipWarn, whipSweep;
    public float whipFrom, whipTo, whipAngle;
    private boolean whipHit;
    // ---- 마왕 1: 머리 포탑
    public boolean headless;
    public float headX, headY, headFromX, headFromY, headFly;
    private float headTimer, headFireT, headLaserT;
    // ---- 마왕 1: 뼈 무더기 붕괴
    public boolean collapsed;
    public float pileX, pileY;
    private float kiteTime, collapseCd;
    // ---- 마왕 2: 군체
    public boolean swarm;
    private int voidCount;
    // ---- 리치: 몇 번째 소환인지 (소환할 때마다 수가 늘어난다)
    private int summonWave;
    // ---- 마왕 2: 공간 절단
    private float rendTimer;
    /** 잘라낼 곳 (예고 중일 때만). */
    public Rectangle rendRect;
    /** 기절 (좀비킹 세 번째 돌진 뒤) 중인지: stagger와 함께 머리 위 별을 그린다. */
    public boolean stunned;

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

    /** 쓰러지는 연출 시간 (프레임 8→9→10 순서로 무너진다). */
    public static final float DEATH_TIME = 2.4f, FINAL_DEATH_TIME = 3.2f;

    @Override
    protected float dyingTime() {
        return finalBoss ? FINAL_DEATH_TIME : DEATH_TIME;
    }

    @Override
    public boolean canBeHit() {
        return super.canBeHit() && !transitioning && !cocoon && !collapsed && !swarm;
    }

    /** 몸을 그리지 않는다 (군체로 흩어졌을 때). */
    public boolean hidden() {
        return swarm;
    }

    public boolean consumeCocoonStart() {
        boolean b = cocoonStarted;
        cocoonStarted = false;
        return b;
    }

    public boolean consumeCocoonEnd() {
        boolean b = cocoonEnded;
        cocoonEnded = false;
        return b;
    }

    /** 머리 그림 가운데 높이 (발밑 기준). */
    public float headHeight() {
        return def.headSrcH > 0 ? 128 - def.headSrcY - def.headSrcH / 2f : 100;
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

    /** 방어막이 깨진 순간 한 번 true (연출용). */
    public boolean consumeArmorBroken() {
        boolean b = armorBroken;
        armorBroken = false;
        return b;
    }

    /** 방금 순간이동했으면 한 번 true. */
    public boolean consumeBlinked() {
        boolean b = blinked;
        blinked = false;
        return b;
    }

    @Override
    public boolean damage(int amount, float fromX, float fromY, float knock) {
        if (!canBeHit()) return false;
        if (def.enrageRatio > 0 && !enraged && hp - amount <= maxHp * def.enrageRatio) {
            // 광폭화: 바람의 고치에 싸여 하늘로
            hp = Math.max(1, Math.round(maxHp * def.enrageRatio));
            flash = 0.2f;
            startCocoon();
            return false;
        }
        if (armor > 0) {
            // 뼈갑옷이 먼저 받는다. 깨기 전에는 리치 체력이 닳지 않는다
            armor = Math.max(0, armor - amount);
            flash = 0.1f;
            if (armor == 0) {
                armorBroken = true;
                stagger = def.armorStagger;
                endPatternQuiet();
                setState(State.RECOVER);
            }
            return false;
        }
        if (def.hasPhase2() && phase == 1 && amount >= hp) {
            hp = 1;
            flash = 0.2f;
            startTransition();
            return false;
        }
        return super.damage(amount, fromX, fromY, knock);
    }

    private void startCocoon() {
        cocoon = true;
        cocoonTime = def.cocoonTime;
        meteorTimer = 1.2f;
        cocoonStarted = true;
        endPatternQuiet();
        clearSpecials();
    }

    /** 패턴 도중 끊길 때 (2페이즈 전환·고치·초기화) 남은 특수 상태를 정리한다. */
    private void clearSpecials() {
        headless = false;
        collapsed = false;
        swarm = false;
        whipWarn = whipSweep = false;
        vanish = 0;
    }

    private void startTransition() {
        clearSpecials();
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
                rendTimer = def.rendFirst;
                transitioning = false;
                transitionEnded = true;
                hp = maxHp;
                patternTimer = 0;
                setState(State.CHASE);
            }
            return;
        }
        blindCooldown = Math.max(0, blindCooldown - delta);
        blinkCooldown = Math.max(0, blinkCooldown - delta);
        if (stagger > 0 && alive()) {
            // 방어막이 깨져 휘청 / 돌진 뒤 기절: 아무것도 못 한다 (공격 기회)
            stagger = Math.max(0, stagger - delta);
            if (stagger <= 0) stunned = false;
            animTime += delta;
            flash = Math.max(0, flash - delta);
            moving = false;
            pose = Pose.IDLE;
            warn = Warn.NONE;
            return;
        }
        collapseCd = Math.max(0, collapseCd - delta);
        if (cocoon && alive()) {
            updateCocoon(delta, ctx);
            return;
        }
        if (headless && alive()) updateHead(delta, ctx);
        // 마왕 본모습: 순서와 상관없이 1분 안팎마다 공간 절단
        if (phase == 2 && def.rendFirst > 0 && alive()) {
            rendTimer -= delta;
            if (rendTimer <= 0 && current == null && !swarm && !collapsed && (state == State.CHASE || state == State.RECOVER)) {
                rendTimer = def.rendIntervalMin + ctx.random().nextFloat() * Math.max(0, def.rendIntervalMax - def.rendIntervalMin);
                startPattern(BossPattern.SPACE_REND, ctx);
                if (current == BossPattern.SPACE_REND) {
                    animTime += delta;
                    return;
                }
            }
        }
        if (!alive() || state == State.SPAWNING || state == State.IDLE || current == null) {
            pose = Pose.IDLE;
            warn = Warn.NONE;
            if (alive() && checkBlink(delta, ctx)) return;
            if (alive() && state != State.SPAWNING && state != State.IDLE && checkCollapse(delta, ctx)) return;
            float speed = (state == State.CHASE ? speedMul() : 1f) * (headless ? def.headlessSpeedMultiplier : 1f);
            super.update(delta * speed, ctx);
            // 머리를 던진 동안 몸통은 달려들기만 한다
            if (alive() && !headless && patterns.length > 0 && (state == State.CHASE || state == State.RECOVER)) {
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
        if (p == BossPattern.VOID_TEAR && voidCount >= def.voidMax) p = BossPattern.TENTACLE; // 공허가 충분하면 촉수로
        if (p == BossPattern.BONE_ARMOR && (armor > 0 || def.armorRatio <= 0)) {
            // 갑옷이 남아 있으면 건너뛰고 다음 공격
            BossPattern next = patterns[nextPattern];
            nextPattern = (nextPattern + 1) % patterns.length;
            if (next != BossPattern.BONE_ARMOR) startPattern(next, ctx);
            return;
        }
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
            case TYPHOON -> {
                pose = Pose.IDLE;
                ctx.sound("wind");
            }
            case BONE_WHIP -> {
                pose = Pose.CAST;
                float pivotY = footY() + 40;
                float aim = (float) Math.toDegrees(Math.atan2(hero.centerY() - pivotY, hero.centerX() - centerX()));
                boolean cw = ctx.random().nextBoolean();
                whipFrom = aim + (cw ? def.whipArc / 2 : -def.whipArc / 2);
                whipTo = aim + (cw ? -def.whipArc / 2 : def.whipArc / 2);
                whipAngle = whipFrom;
                whipWarn = true;
                whipSweep = false;
                whipHit = false;
                ctx.sound("whip_charge");
            }
            case COLLAPSE -> {
                collapsed = true;
                pileX = footX();
                pileY = footY();
                pose = Pose.IDLE;
                ctx.sound("bone_collapse");
            }
            case SWARM -> {
                swarm = true;
                ctx.sound("swarm");
                ctx.shake(5, 0.6f);
            }
            case SPACE_REND -> {
                rendRect = ctx.planRend(this);
                if (rendRect == null) {
                    // 더 자를 곳이 없다: 공허 균열로 대신
                    endPatternQuiet();
                    setState(State.RECOVER);
                    return;
                }
                pose = Pose.CAST;
                ctx.sound("rend_charge");
                ctx.shake(3, 0.5f);
            }
            case NOVA, SUMMON, BONE_SPIKE, TENTACLE, BONE_ARMOR, SWAMP, HEAD_TURRET, BONE_STORM, EYES, ABYSS_SIGIL,
                 VOID_TEAR, WARP_TENTACLE, FLESH_TIDE -> {
                pose = Pose.CAST;
                ctx.sound("cast");
                // 뼈 가시·소환과 함께 실명 (한 번 건 뒤에는 잠시 쉰다)
                if ((p == BossPattern.BONE_SPIKE || p == BossPattern.SUMMON) && def.blindDuration > 0 && blindCooldown <= 0) {
                    ctx.blindHero(def.blindDuration);
                    blindCooldown = def.blindCooldown + def.blindDuration;
                }
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
                // 먼저 뒤돌아선다 (그다음 빙글빙글)
                pose = Pose.BACK;
                spinDuration = def.spinMin + ctx.random().nextFloat() * Math.max(0, def.spinMax - def.spinMin);
                fireTimer = 0;
                spinAngle = 180;
                ctx.sound("dragon_turn");
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
                    fireNova(ctx, n, phase == 2 ? (float) (Math.PI / n) : 0);
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
                    summonWave++;
                    int[] n = summonCounts(summonWave);
                    // 해골 기사를 먼저 (전체 상한에 막히지 않게)
                    if (def.summonExtraId != null && n[1] > 0) ctx.summon(this, def.summonExtraId, n[1], def.summonMax);
                    ctx.summon(this, def.summonId, n[0], def.summonMax);
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
                                .with(Projectile.Status.FREEZE).boss());
                    }
                    ctx.shake(4, 0.3f);
                    ctx.sound("ice_burst");
                }
                if (patternTime >= 1.3f) endPattern();
            }
            case FIRE_SPIN -> {
                if (patternTime < def.spinTurnTime) {
                    pose = Pose.BACK;   // 뒤돌아서는 중
                    break;
                }
                pose = Pose.SPIN;
                spinAngle = (spinAngle + delta * def.spinSpeed) % 360f;
                fireTimer += delta;
                while (fireTimer >= def.fireRate) {
                    fireTimer -= def.fireRate;
                    // 바라보는 쪽 (0도 = 화면 아래)으로 두 갈래, 꼬리 쪽으로 하나
                    double face = Math.toRadians(spinAngle);
                    for (int k = 0; k < 3; k++) {
                        double yaw = face + (k == 2 ? Math.PI : (k == 0 ? -0.18 : 0.18));
                        float dx = (float) Math.sin(yaw), dy = (float) -Math.cos(yaw);
                        float sp = def.fireSpeed;
                        ctx.spawnProjectile(new Projectile(centerX() + dx * 34, centerY() + 26 + dy * 22, dx * sp, dy * sp,
                                10, Math.round(attack * def.fireMultiplier), "fire_orb", 3.5f)
                                .with(Projectile.Status.BURN).boss());
                    }
                }
                if (patternTime >= def.spinTurnTime + spinDuration) {
                    spinAngle = 0;
                    endPattern();
                }
            }
            case TYPHOON -> runTyphoon(ctx, hero);
            case BONE_WHIP -> runWhip(ctx, hero);
            case HEAD_TURRET -> {
                if (step == 0 && patternTime >= 0.5f) {
                    step = 1;
                    Rectangle a = ctx.arenaOf(this);
                    // 용사에게서 가장 먼 구석
                    float bestD = -1;
                    for (int cx = 0; cx < 2; cx++) {
                        for (int cy = 0; cy < 2; cy++) {
                            float x = a.x + (cx == 0 ? 70 : a.width - 70), y = a.y + (cy == 0 ? 70 : a.height - 50);
                            float d = (float) Math.hypot(x - hero.footX(), y - hero.footY());
                            if (d > bestD) {
                                bestD = d;
                                headX = x;
                                headY = y;
                            }
                        }
                    }
                    headFromX = centerX();
                    headFromY = footY() + headHeight();
                    headFly = 0;
                    headless = true;
                    headTimer = def.headTime;
                    headFireT = 0.6f;
                    headLaserT = 1.6f;
                    ctx.sound("head_throw");
                }
                if (patternTime >= 1.0f) endPattern();
            }
            case BONE_STORM -> {
                if (step == 0 && patternTime >= 0.6f) {
                    step = 1;
                    Rectangle a = ctx.arenaOf(this);
                    float[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    float[] d = dirs[ctx.random().nextInt(4)];
                    float rx = a.x + a.width / 2, ry = a.y + a.height / 2;
                    for (int tries = 0; tries < 40; tries++) {
                        rx = a.x + 90 + ctx.random().nextFloat() * (a.width - 180);
                        ry = a.y + 90 + ctx.random().nextFloat() * (a.height - 180);
                        float dist = (float) Math.hypot(rx - hero.footX(), ry - hero.footY());
                        Rectangle probe = new Rectangle(rx - def.rockWidth / 2, ry, def.rockWidth, def.rockHeight);
                        if (dist > 110 && dist < 280 && !ctx.collision().blocked(probe) && !probe.overlaps(body)) break;
                    }
                    Hazard h = new Hazard(Hazard.Type.BONE_STORM, rx, ry, def.shelterLength, 0, def.stormWarning, def.stormTime, 0);
                    h.area = new Rectangle(a);
                    h.dirX = d[0];
                    h.dirY = d[1];
                    h.ratio = def.stormDamageRatio;
                    h.rock = new Rectangle(rx - def.rockWidth / 2, ry, def.rockWidth, def.rockHeight);
                    ctx.addHazard(h);
                    ctx.sound("bone_grind");
                }
                if (step == 1 && patternTime >= 0.6f + def.stormWarning + def.stormTime) endPattern();
            }
            case COLLAPSE -> runCollapse(ctx, hero);
            case EYES -> {
                int eyes = Math.max(1, def.eyeCount);
                if (step == 0 && patternTime >= 0.4f) {
                    step = 1;
                    // 심연의 눈 여러 개: 용사를 둘러싸듯 서로 다른 쪽(용사에게서 160~230px)에 뜨고,
                    // eyeGap초 간격으로 하나씩 빛을 뿜는다 → 차례대로 그 눈을 등져야 한다
                    Rectangle a = ctx.arenaOf(this);
                    double base = ctx.random().nextFloat() * Math.PI * 2;
                    for (int i = 0; i < eyes; i++) {
                        double ang = base + Math.PI * 2 * i / eyes + (ctx.random().nextFloat() - 0.5f) * 0.4f;
                        float r = 160 + ctx.random().nextFloat() * 70;
                        float ex = clampX(a, hero.centerX() + (float) Math.cos(ang) * r);
                        float ey = clampY(a, hero.centerY() + 20 + (float) Math.sin(ang) * r * 0.6f);
                        Hazard h = new Hazard(Hazard.Type.EYE, ex, ey, 40, 0, def.eyeWarning + i * def.eyeGap, 0, 0);
                        h.eyeAngle = def.eyeAngle;
                        h.eyeStun = def.eyeStun;
                        h.eyeSeal = def.eyeSeal;
                        ctx.addHazard(h);
                    }
                    ctx.sound("eye_open");
                }
                // 동시에 사방 탄: 눈을 등진 채 피해야 한다
                if (step == 1 && patternTime >= 0.4f + def.eyeWarning * 0.4f) {
                    step = 2;
                    fireNova(ctx, def.novaCount, 0);
                }
                if (patternTime >= 0.4f + def.eyeWarning + (eyes - 1) * def.eyeGap + 0.3f) endPattern();
            }
            case SPACE_REND -> {
                warnProgress = patternTime / def.rendWarning;
                if (step == 0 && patternTime >= def.rendWarning) {
                    step = 1;
                    ctx.applyRend(rendRect);
                    Effect e = new Effect(Effect.Type.REND, rendRect.x + rendRect.width / 2, rendRect.y + rendRect.height / 2, 0.9f);
                    e.rect = new Rectangle(rendRect);
                    ctx.effect(e);
                    ctx.shake(10, 0.8f);
                    ctx.sound("space_rend");
                }
                if (step == 1 && patternTime >= def.rendWarning + 0.7f) {
                    rendRect = null;
                    endPattern();
                }
            }
            case ABYSS_SIGIL -> {
                if (step == 0 && patternTime >= 0.45f) {
                    step = 1;
                    ctx.addHazard(new Hazard(Hazard.Type.ABYSS_SIGIL, hero.footX(), hero.footY(), def.sigilRadius, 0, 0.6f,
                            def.sigilLife, 0));
                    ctx.sound("sigil");
                }
                if (patternTime >= 0.8f) endPattern();
            }
            case VOID_TEAR -> {
                if (step == 0 && patternTime >= 0.6f) {
                    step = 1;
                    Rectangle a = ctx.arenaOf(this);
                    for (int k = 0; k < 2 && voidCount < def.voidMax; k++) {
                        float px = a.x + a.width / 2, py = a.y + a.height / 2;
                        for (int tries = 0; tries < 30; tries++) {
                            px = a.x + 60 + ctx.random().nextFloat() * (a.width - 120);
                            py = a.y + 60 + ctx.random().nextFloat() * (a.height - 120);
                            if (Math.hypot(px - hero.footX(), py - hero.footY()) > 70) break;
                        }
                        double ang = ctx.random().nextFloat() * Math.PI;
                        float len = 120 + ctx.random().nextFloat() * 80; // 가운데에서 양쪽으로
                        float x1 = px - (float) Math.cos(ang) * len, y1 = py - (float) Math.sin(ang) * len;
                        float x2 = px + (float) Math.cos(ang) * len, y2 = py + (float) Math.sin(ang) * len;
                        Hazard h = Hazard.line(Hazard.Type.VOID, clampX(a, x1), clampY(a, y1), clampX(a, x2), clampY(a, y2),
                                def.voidWidth, k * 0.25f, def.voidWarning, Hazard.FOREVER, 0);
                        h.ratio = def.voidDamageRatio;
                        ctx.addHazard(h);
                        voidCount++;
                    }
                    ctx.sound("void_tear");
                    ctx.shake(4, 0.4f);
                }
                if (patternTime >= 1.1f) endPattern();
            }
            case WARP_TENTACLE -> {
                if (step == 0 && patternTime >= 0.3f) {
                    step = 1;
                    Rectangle a = ctx.arenaOf(this);
                    // 진짜는 용사 등 뒤 벽에서: 오른쪽을 보고 있으면 왼쪽 벽
                    boolean fromLeft = Math.abs(hero.dirX) > 0.2f ? hero.dirX > 0 : ctx.random().nextBoolean();
                    float x0 = fromLeft ? a.x : a.x + a.width, x1 = fromLeft ? a.x + a.width * 0.72f : a.x + a.width * 0.28f;
                    float wy = hero.centerY();
                    Hazard h = Hazard.line(Hazard.Type.WARP, x0, wy, x1, wy, def.warpWidth, 0, def.warpWarning, 0,
                            Math.round(attack * def.warpMultiplier));
                    h.decoyX = fromLeft ? a.x + a.width : a.x;
                    h.decoyY = centerY() + 30;
                    ctx.addHazard(h);
                    // 보이는 쪽: 반대쪽 벽으로 촉수를 찌른다
                    warn = Warn.LINE;
                    warnDirX = fromLeft ? 1 : -1;
                    warnDirY = 0;
                    warnX = centerX();
                    warnY = centerY() + 30;
                    warnLength = Math.abs(h.decoyX - centerX());
                    faceLeft = !fromLeft;
                    ctx.sound("warp");
                }
                if (step == 1) warnProgress = Math.min(1f, (patternTime - 0.3f) / def.warpWarning);
                if (patternTime >= 0.3f + def.warpWarning + 0.45f) endPattern();
            }
            case FLESH_TIDE -> {
                if (step == 0 && patternTime >= 0.5f) {
                    step = 1;
                    Rectangle a = ctx.arenaOf(this);
                    float total = 1.2f + def.tideWaves * def.tideGap + 2.5f;
                    java.util.List<float[]> placed = new java.util.ArrayList<>();
                    for (int i = 0; i < def.crackCount; i++) {
                        float cx = a.x + a.width / 2, cy = a.y + a.height / 2;
                        for (int tries = 0; tries < 40; tries++) {
                            cx = a.x + 50 + ctx.random().nextFloat() * (a.width - 100);
                            cy = a.y + 50 + ctx.random().nextFloat() * (a.height - 100);
                            boolean ok = Math.hypot(cx - footX(), cy - footY()) > 90; // 보스 발밑은 피한다
                            for (float[] q : placed) if (Math.hypot(q[0] - cx, q[1] - cy) < 110) ok = false;
                            if (ok) break;
                        }
                        placed.add(new float[]{cx, cy});
                        ctx.addHazard(new Hazard(Hazard.Type.CRACK, cx, cy, def.crackRadius, i * 0.06f, 0.4f, total, 0));
                    }
                    float[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int w = 0; w < def.tideWaves; w++) {
                        float[] d = dirs[ctx.random().nextInt(4)];
                        Hazard h = Hazard.sweep(Hazard.Type.FLESH_TIDE, a, d[0], d[1], def.tideSpeed, 1.2f + w * def.tideGap, 1.0f, 0);
                        h.ratio = def.tideHeroRatio;
                        ctx.addHazard(h);
                    }
                    ctx.sound("tide");
                }
                if (patternTime >= 0.5f + 1.2f + def.tideWaves * def.tideGap + 1.0f) endPattern();
            }
            case SWARM -> {
                if (step == 0 && patternTime >= 0.6f) {
                    step = 1;
                    Rectangle a = ctx.arenaOf(this);
                    for (int r = 0; r < def.swarmRows; r++) {
                        boolean vertical = ctx.random().nextBoolean();
                        boolean forward = ctx.random().nextBoolean();
                        float dx = vertical ? 0 : (forward ? 1 : -1), dy = vertical ? (forward ? 1 : -1) : 0;
                        Hazard h = Hazard.sweep(Hazard.Type.SWARM_ROW, a, dx, dy, def.swarmSpeed, r * def.swarmRowGap, 0.7f,
                                Math.round(attack * def.swarmMultiplier));
                        float span = vertical ? a.width : a.height;
                        int n = 1 + ctx.random().nextInt(2);
                        h.gaps = new float[n];
                        for (int g = 0; g < n; g++) h.gaps[g] = def.swarmGapWidth + ctx.random().nextFloat() * (span - def.swarmGapWidth * 2);
                        h.gapWidth = def.swarmGapWidth;
                        ctx.addHazard(h);
                    }
                }
                float rowsEnd = 0.6f + def.swarmRows * def.swarmRowGap + 0.7f + 3.5f;
                if (patternTime >= Math.max(def.swarmTime, rowsEnd)) {
                    swarm = false;
                    Rectangle a = ctx.arenaOf(this);
                    teleport(a.x + a.width / 2, a.y + a.height * 0.62f);
                    ctx.effect(new Effect(Effect.Type.SUMMON, centerX(), centerY() + 40, 0.8f));
                    ctx.shake(6, 0.5f);
                    ctx.sound("reform");
                    endPattern();
                }
            }
            case BONE_ARMOR -> {
                if (step == 0 && patternTime >= 0.9f) {
                    step = 1;
                    armorMax = Math.round(maxHp * def.armorRatio);
                    armor = armorMax;
                    ctx.sound("bone_armor");
                    ctx.effect(new Effect(Effect.Type.SUMMON, centerX(), centerY() + 30, 0.6f));
                }
                if (patternTime >= 1.2f) endPattern();
            }
            case SWAMP -> {
                if (step == 0 && patternTime >= 0.6f) {
                    step = 1;
                    Rectangle area = ctx.arenaOf(this);
                    for (int i = 0; i < def.swampCount; i++) {
                        float x, y;
                        if (i == 0) {
                            x = hero.footX();
                            y = hero.footY();
                        } else {
                            float[] pt = randomPoint(ctx, area, def.swampMaxRadius * 0.6f);
                            x = pt[0];
                            y = pt[1];
                        }
                        ctx.addHazard(Hazard.swamp(x, y, def.swampStartRadius, def.swampMaxRadius, def.swampGrowTime,
                                i * 0.12f, 0.5f, def.swampLife));
                    }
                    ctx.sound("swamp");
                }
                // 이어서 뼈창: 늪에 발이 묶인 용사를 노린다
                if (step == 1 && patternTime >= 0.6f + def.spearDelay) {
                    step = 2;
                    float ox = centerX(), oy = footY() + 6;
                    double aim = Math.atan2(hero.footY() - oy, hero.footX() - ox);
                    for (int i = 0; i < def.spearCount; i++) {
                        double a = aim + (i == 0 ? 0 : (i % 2 == 1 ? 1 : -1) * 0.42 * ((i + 1) / 2));
                        // 방 밖으로 뻗지 않게 벽 앞에서 자른다
                        Rectangle area = ctx.arenaOf(this);
                        float len = def.spearLength;
                        float ex = ox + (float) Math.cos(a) * len, ey = oy + (float) Math.sin(a) * len;
                        while (len > 40 && !area.contains(ex, ey)) {
                            len -= 8;
                            ex = ox + (float) Math.cos(a) * len;
                            ey = oy + (float) Math.sin(a) * len;
                        }
                        ctx.addHazard(Hazard.spear(ox, oy, ex, ey, def.spearWidth, i == 0 ? 0 : 0.12f, def.spearWarning,
                                Math.round(attack * def.spearMultiplier)));
                    }
                    ctx.sound("cast");
                }
                if (step == 2 && patternTime >= 0.6f + def.spearDelay + 0.4f) endPattern();
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
                if (def.rushStun > 0) {
                    // 세 번째 돌진 뒤 기절: 숨을 몰아쉬며 아무것도 못 한다
                    stagger = def.rushStun;
                    stunned = true;
                    ctx.sound("stun");
                }
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

    private void fireNova(CombatContext ctx, int n, float offset) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + offset;
            float sp = def.projectileSpeed;
            ctx.spawnProjectile(new Projectile(centerX(), centerY() + 20, (float) Math.cos(a) * sp,
                    (float) Math.sin(a) * sp, 10, Math.round(attack * 0.7f), def.projectile, 4f).boss());
        }
        ctx.sound("nova");
    }

    private static float clampX(Rectangle a, float x) {
        return Math.max(a.x + 8, Math.min(a.x + a.width - 8, x));
    }

    private static float clampY(Rectangle a, float y) {
        return Math.max(a.y + 8, Math.min(a.y + a.height - 8, y));
    }

    // =================================================================== 마룡: 태풍·광폭화

    /** 사라졌다가 맵 가운데에 나타나 사방으로 태풍 여러 겹. */
    private void runTyphoon(CombatContext ctx, HeroUnit hero) {
        if (step == 0) {
            vanish = Math.min(1f, patternTime / 0.35f);
            if (patternTime >= 0.35f) {
                step = 1;
                Rectangle a = ctx.arenaOf(this);
                teleport(a.x + a.width / 2, a.y + a.height / 2 - body.height);
                ctx.effect(new Effect(Effect.Type.SUMMON, centerX(), centerY() + 40, 0.6f));
                ctx.shake(4, 0.3f);
            }
            return;
        }
        vanish = Math.max(0, 1f - (patternTime - 0.35f) / 0.3f);
        pose = Pose.HOWL_UP;
        float start = 0.95f, gap = 0.45f;
        while (step - 1 < def.typhoonRings && patternTime >= start + (step - 1) * gap) {
            int r = step - 1;
            for (int i = 0; i < def.typhoonCount; i++) {
                double a = Math.PI * 2 * i / def.typhoonCount + r * Math.PI / def.typhoonCount;
                float sp = def.typhoonSpeed;
                ctx.spawnProjectile(new Projectile(centerX(), centerY() + 20, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                        12, Math.round(attack * def.typhoonMultiplier), "tornado", 4.5f).knock(def.typhoonKnockback).boss());
            }
            ctx.sound("typhoon");
            step++;
        }
        if (step - 1 >= def.typhoonRings && patternTime >= start + def.typhoonRings * gap + 0.3f) {
            vanish = 0;
            endPattern();
        }
    }

    /** 바람의 고치: 공중에서 cocoonTime초. 메테오를 계속 떨어뜨린다. 끝나면 광폭화(공격력 ×1.5). */
    private void updateCocoon(float delta, CombatContext ctx) {
        animTime += delta;
        flash = Math.max(0, flash - delta);
        moving = false;
        pose = Pose.HOWL_UP;
        warn = Warn.NONE;
        cocoonTime -= delta;
        meteorTimer -= delta;
        if (meteorTimer <= 0 && cocoonTime > def.meteorWarning + 0.4f) {
            meteorTimer = def.meteorInterval;
            Rectangle a = ctx.arenaOf(this);
            float m = def.meteorSafeRadius + 30;
            float sx = a.x + m + ctx.random().nextFloat() * (a.width - m * 2);
            float sy = a.y + m + ctx.random().nextFloat() * (a.height - m * 2);
            ctx.addHazard(Hazard.meteor(a, sx, sy, def.meteorSafeRadius, def.meteorWarning,
                    Math.round(attack * def.meteorMultiplier)));
            ctx.sound("meteor_warn");
        }
        if (cocoonTime <= 0) {
            cocoon = false;
            enraged = true;
            attack = Math.round(attack * def.enrageAttackMultiplier);
            cocoonEnded = true;
            pose = Pose.IDLE;
            ctx.shake(8, 0.7f);
            ctx.sound("boss_roar");
            setState(State.RECOVER);
        }
    }

    // =================================================================== 마왕 1: 채찍·머리·붕괴

    /** 예고(부채꼴) 뒤 척추 채찍이 한 번 휩쓴다. 지나가는 순간 회피 무적이면 안 맞는다. */
    private void runWhip(CombatContext ctx, HeroUnit hero) {
        if (patternTime < def.whipWarning) {
            warnProgress = patternTime / def.whipWarning;
            return;
        }
        if (whipWarn) {
            whipWarn = false;
            whipSweep = true;
            ctx.sound("whip");
            ctx.shake(3, 0.2f);
        }
        float k = Math.min(1f, (patternTime - def.whipWarning) / def.whipSweepTime);
        float prev = whipAngle;
        whipAngle = whipFrom + (whipTo - whipFrom) * k;
        if (!whipHit) {
            float px = centerX(), py = footY() + 40;
            float dx = hero.centerX() - px, dy = hero.centerY() - py;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float a = (float) Math.toDegrees(Math.atan2(dy, dx));
            if (dist <= def.whipRadius + 8 && passed(a, prev, whipAngle)) {
                whipHit = true;
                ctx.hitHeroArea(Math.round(attack * def.whipMultiplier), px, py);
            }
        }
        if (k >= 1f && patternTime >= def.whipWarning + def.whipSweepTime + 0.25f) {
            whipSweep = false;
            endPattern();
        }
    }

    /** 각도 a가 from→to 사이를 지났는지 (도, 감김 처리). */
    private static boolean passed(float a, float from, float to) {
        float span = to - from;
        float rel = wrap(a - from);
        if (span >= 0) return rel >= -0.5f && rel <= span + 0.5f;
        return rel <= 0.5f && rel >= span - 0.5f;
    }

    private static float wrap(float d) {
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    /** 던진 머리: 날아간 뒤 유도탄과 레이저를 쏜다. 시간이 다 되면 몸으로 돌아온다. */
    private void updateHead(float delta, CombatContext ctx) {
        HeroUnit hero = ctx.heroUnit();
        headFly = Math.min(1f, headFly + delta * 2f);
        if (headFly < 1f) return;
        headTimer -= delta;
        headFireT -= delta;
        headLaserT -= delta;
        if (headFireT <= 0) {
            headFireT = def.headFireRate;
            float dx = hero.centerX() - headX, dy = hero.centerY() + 6 - headY;
            float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
            float sp = def.headHomingSpeed;
            ctx.spawnProjectile(new Projectile(headX, headY, dx / len * sp, dy / len * sp, 8, Math.round(attack * 0.6f),
                    "bolt_red", 4f).homing(def.headTurn, 2.2f).boss());
            ctx.sound("bolt");
        }
        if (headLaserT <= 0) {
            headLaserT = def.headLaserEvery;
            Rectangle a = ctx.arenaOf(this);
            float dx = hero.centerX() - headX, dy = hero.centerY() - headY;
            float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
            float ex = headX + dx / len * 900, ey = headY + dy / len * 900;
            float l = 900;
            while (l > 40 && !a.contains(ex, ey)) {
                l -= 10;
                ex = headX + dx / len * l;
                ey = headY + dy / len * l;
            }
            ctx.addHazard(Hazard.line(Hazard.Type.LASER, headX, headY, ex, ey, def.headLaserWidth, 0, def.headLaserWarning, 0,
                    Math.round(attack * def.headLaserMultiplier)));
            ctx.sound("laser_charge");
        }
        if (headTimer <= 0) {
            headless = false;
            ctx.effect(new Effect(Effect.Type.SUMMON, centerX(), footY() + headHeight(), 0.5f));
            ctx.sound("reform");
        }
    }

    /** 거리를 벌리면(collapseRange 밖에 collapseKiteTime초) 무너진다. */
    private boolean checkCollapse(float delta, CombatContext ctx) {
        if (def.collapseRange <= 0 || headless || (def.hasPhase2() && phase != 1)) return false;
        float d = distanceTo(ctx.heroUnit());
        if (d > def.collapseRange) kiteTime += delta;
        else kiteTime = Math.max(0, kiteTime - delta);
        if (kiteTime < def.collapseKiteTime || collapseCd > 0) return false;
        kiteTime = 0;
        startPattern(BossPattern.COLLAPSE, ctx);
        return true;
    }

    /** 무너져 무적 → 용사 등 뒤에서 다시 모여 → 짧은 예고 뒤 내려찍기. */
    private void runCollapse(CombatContext ctx, HeroUnit hero) {
        if (step == 0) {
            if (patternTime >= def.collapseInvuln) {
                step = 1;
                stepTime = 0;
                Rectangle a = ctx.arenaOf(this);
                float bx = hero.footX() - hero.dirX * 72, by = hero.footY() - hero.dirY * 72;
                bx = Math.max(a.x + body.width, Math.min(a.x + a.width - body.width, bx));
                by = Math.max(a.y + 10, Math.min(a.y + a.height - 40, by));
                teleport(bx, by);
                faceLeft = hero.centerX() < centerX();
                collapsed = false;
                warn = Warn.CIRCLE;
                warnX = hero.centerX();
                warnY = hero.centerY();
                warnRadius = def.collapseSlamRadius;
                pose = Pose.CAST;
                ctx.effect(new Effect(Effect.Type.SUMMON, centerX(), centerY() + 30, 0.5f));
                ctx.sound("reform");
            }
            return;
        }
        if (step == 1) {
            warnProgress = stepTime / def.collapseSlamWarning;
            if (stepTime >= def.collapseSlamWarning) {
                step = 2;
                Effect e = new Effect(Effect.Type.SLAM, warnX, warnY, 0.35f);
                e.radius = warnRadius;
                ctx.effect(e);
                ctx.shake(6, 0.25f);
                ctx.sound("slam");
                float dx = hero.centerX() - warnX, dy = hero.centerY() - warnY;
                if (dx * dx + dy * dy <= warnRadius * warnRadius) {
                    ctx.hitHeroArea(Math.round(attack * def.collapseMultiplier), warnX, warnY);
                }
                warn = Warn.NONE;
            }
            return;
        }
        if (stepTime >= def.collapseSlamWarning + 0.3f) {
            collapseCd = def.collapseCooldown;
            endPattern();
        }
    }

    /**
     * 구석 몰이 방지 (리치): 용사가 blinkCloseTime초 넘게 바짝 붙어 있으면 방 반대편으로 순간이동하고
     * 용사에게 탄 세 발을 쏜다. 순간이동했으면 true.
     */
    private boolean checkBlink(float delta, CombatContext ctx) {
        if (def.blinkCloseTime <= 0) return false;
        HeroUnit hero = ctx.heroUnit();
        float d = distanceTo(hero);
        if (d < 96) closeTime += delta;
        else closeTime = Math.max(0, closeTime - delta * 0.5f);
        if (closeTime < def.blinkCloseTime || blinkCooldown > 0) return false;
        Rectangle area = ctx.arenaOf(this);
        float margin = 70;
        for (int tries = 0; tries < 30; tries++) {
            float x = area.x + margin + ctx.random().nextFloat() * Math.max(1, area.width - margin * 2);
            float y = area.y + margin + ctx.random().nextFloat() * Math.max(1, area.height - margin * 2);
            if (Math.hypot(x - hero.footX(), y - hero.footY()) < 230) continue;
            Rectangle probe = new Rectangle(x - body.width / 2, y, body.width, body.height);
            if (ctx.collision().blocked(probe)) continue;
            Effect from = new Effect(Effect.Type.SUMMON, centerX(), centerY() + 30, 0.45f);
            ctx.effect(from);
            teleport(x, y);
            Effect to = new Effect(Effect.Type.SUMMON, centerX(), centerY() + 30, 0.45f);
            ctx.effect(to);
            ctx.sound("blink");
            // 나타나자마자 세 갈래 탄
            float dx = hero.centerX() - centerX(), dy = hero.centerY() - (centerY() + 30);
            double aim = Math.atan2(dy, dx);
            for (int k = -1; k <= 1; k++) {
                double a = aim + k * 0.22;
                float sp = def.projectileSpeed;
                ctx.spawnProjectile(new Projectile(centerX(), centerY() + 30, (float) Math.cos(a) * sp,
                        (float) Math.sin(a) * sp, 8, attack, def.projectile, 3f).boss());
            }
            closeTime = 0;
            blinkCooldown = def.blinkCooldown;
            blinked = true;
            setState(State.RECOVER);
            return true;
        }
        return false;
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

    /** n번째 소환 때 부를 수: [첫째 소환수, 둘째 소환수]. */
    public int[] summonCounts(int wave) {
        int a = Math.min(def.summonCount * wave, def.summonCap);
        int b = def.summonExtraId == null ? 0 : Math.min(def.summonExtraCount * wave, def.summonExtraCap);
        return new int[]{a, b};
    }

    private void endPatternQuiet() {
        rendRect = null;
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
        armor = armorMax = 0;
        stagger = 0;
        stunned = false;
        summonWave = 0;
        clearSpecials();
        cocoon = enraged = false;
        phase = 1;
        transitioning = false;
        setState(State.IDLE);
    }
}
