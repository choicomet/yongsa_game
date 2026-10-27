package com.yongsa.combat;

/**
 * 몬스터 AI (FR-전투-06, 구현안 5장): 대기 → 쫓기 → 예고(멈춰서 깜빡임) → 공격 → 쉬기.
 * 근접형은 예고 뒤 짧게 돌진하고, 원거리형은 투사체를 쏜다. 예고를 보고 걸어서 피할 수 있다.
 */
public final class MonsterAI {
    private MonsterAI() {
    }

    public static void update(Monster m, float delta, CombatContext ctx) {
        HeroUnit hero = ctx.heroUnit();
        switch (m.state) {
            case SPAWNING -> {
                if (m.stateTime >= Monster.SPAWN_TIME) m.setState(Monster.State.IDLE);
            }
            case IDLE -> {
                if (ctx.heroAlive() && m.distanceTo(hero) <= m.def.detectRange) m.setState(Monster.State.CHASE);
            }
            case CHASE -> chase(m, delta, ctx, hero);
            case WINDUP -> {
                faceHero(m, hero);
                if (m.stateTime >= m.def.windup) {
                    if (m.def.isRanged()) {
                        shoot(m, ctx, hero);
                        m.setState(Monster.State.RECOVER);
                    } else {
                        float dx = hero.centerX() - m.centerX(), dy = hero.centerY() - m.centerY();
                        float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
                        m.lungeX = dx / len;
                        m.lungeY = dy / len;
                        m.hitThisAttack = false;
                        m.setState(Monster.State.ATTACK);
                        // 공격 모션 궤적: 기사는 크게 베고, 병사는 앞으로 찌른다
                        if (!m.isBoss()) {
                            Effect e = new Effect(m.def.slashes() ? Effect.Type.MOB_SLASH : Effect.Type.MOB_THRUST,
                                    m.centerX(), m.centerY() + 10, Monster.ATTACK_TIME + 0.08f);
                            e.dirX = m.lungeX;
                            e.dirY = m.lungeY;
                            e.radius = m.def.attackRange + 10;
                            e.arc = m.def.slashArc;
                            ctx.effect(e);
                            ctx.sound(m.def.slashes() ? "mob_slash" : "mob_thrust");
                        }
                    }
                }
            }
            case ATTACK -> {
                if (m.def.slashes()) {
                    // 베기: 제자리에서 앞쪽 부채꼴. 휘두르는 한가운데에서 한 번 판정
                    if (!m.hitThisAttack && m.stateTime >= Monster.ATTACK_TIME * 0.4f) {
                        m.hitThisAttack = true;
                        if (inSlash(m, hero)) ctx.hitHero(m.attack, m.centerX(), m.centerY());
                    }
                    if (m.stateTime >= Monster.ATTACK_TIME) m.setState(Monster.State.RECOVER);
                    return;
                }
                ctx.collision().move(m.body, m.lungeX * m.def.lunge * delta, m.lungeY * m.def.lunge * delta);
                if (!m.hitThisAttack && touching(m, hero, 4)) {
                    m.hitThisAttack = true;
                    if (m.isBoss()) ctx.hitHeroHeavy(m.attack, m.centerX(), m.centerY());
                    else ctx.hitHero(m.attack, m.centerX(), m.centerY());
                }
                if (m.stateTime >= Monster.ATTACK_TIME) m.setState(Monster.State.RECOVER);
            }
            case RECOVER -> {
                if (m.stateTime >= m.def.cooldown) m.setState(Monster.State.CHASE);
            }
            case DYING -> { }
        }
    }

    private static void chase(Monster m, float delta, CombatContext ctx, HeroUnit hero) {
        if (!ctx.heroAlive()) {
            m.setState(Monster.State.IDLE);
            return;
        }
        float dist = m.distanceTo(hero);
        float reach = m.def.attackRange;
        if (dist <= reach) {
            m.setState(Monster.State.WINDUP);
            return;
        }
        float dx = (hero.centerX() - m.centerX()) / dist, dy = (hero.centerY() - m.centerY()) / dist;
        m.faceLeft = dx < 0;
        m.moving = true;
        ctx.collision().move(m.body, dx * m.def.speed * delta, dy * m.def.speed * delta);
    }

    private static void faceHero(Monster m, HeroUnit hero) {
        m.faceLeft = hero.centerX() < m.centerX();
    }

    static void shoot(Monster m, CombatContext ctx, HeroUnit hero) {
        float dx = hero.centerX() - m.centerX(), dy = hero.centerY() + 6 - (m.centerY() + 14);
        float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
        float sp = m.def.projectileSpeed;
        Projectile p = new Projectile(m.centerX(), m.centerY() + 14, dx / len * sp, dy / len * sp, 8,
                m.attack, m.def.projectile, 3f);
        if (m.isBoss()) p.boss();
        ctx.spawnProjectile(p);
        ctx.sound("bolt");
    }

    /** 베기 범위: 사거리 + 10 안이고, 휘두른 방향에서 slashArc/2도 안. */
    static boolean inSlash(Monster m, HeroUnit hero) {
        float dx = hero.centerX() - m.centerX(), dy = hero.centerY() - m.centerY();
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > m.def.attackRange + 10 + hero.body.width / 2) return false;
        if (d < 10) return true;
        float cos = (dx * m.lungeX + dy * m.lungeY) / d;
        return cos >= Math.cos(Math.toRadians(m.def.slashArc / 2));
    }

    static boolean touching(Monster m, HeroUnit hero, float margin) {
        return hero.body.x < m.body.x + m.body.width + margin && hero.body.x + hero.body.width > m.body.x - margin
                && hero.body.y < m.body.y + m.body.height + margin && hero.body.y + hero.body.height > m.body.y - margin;
    }
}
