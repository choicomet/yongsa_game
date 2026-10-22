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
                    }
                }
            }
            case ATTACK -> {
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
        ctx.spawnProjectile(new Projectile(m.centerX(), m.centerY() + 14, dx / len * sp, dy / len * sp, 8,
                m.attack, m.def.projectile, 3f));
        ctx.sound("bolt");
    }

    static boolean touching(Monster m, HeroUnit hero, float margin) {
        return hero.body.x < m.body.x + m.body.width + margin && hero.body.x + hero.body.width > m.body.x - margin
                && hero.body.y < m.body.y + m.body.height + margin && hero.body.y + hero.body.height > m.body.y - margin;
    }
}
