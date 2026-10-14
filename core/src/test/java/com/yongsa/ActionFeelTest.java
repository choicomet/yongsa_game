package com.yongsa;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.yongsa.combat.*;
import com.yongsa.model.*;
import com.yongsa.ui.KeyboardInput;
import com.yongsa.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression tests for the reported hold delay, missing actions and keyboard-only controls. */
class ActionFeelTest {
    final com.yongsa.config.Balance b = TestData.balance();
    final float dt = GameWorld.STEP;

    GameWorld world() {
        GameState s = TestData.newGame(); s.ore[0] = 20;
        CraftService.craft(b, s, "sword");
        return new GameWorld(b, DungeonGenerator.generate(12345, b), s);
    }
    PlayerInput keys() { PlayerInput i = new PlayerInput(); i.keyboardAim = true; return i; }
    void step(GameWorld w, PlayerInput i, int frames) {
        for (int f=0; f<frames; f++) { w.step(dt, i); i.clearEdges(); w.events.clear(); }
    }
    void arena(GameWorld w) {
        RoomDef room = w.map.firstCombatRoom("mine");
        w.hero.x = room.centerX(); w.hero.y = room.centerY();
        w.step(dt, keys());
        for (Enemy e : w.combat.enemies) { e.grace=1000; e.x=room.centerX()+230; e.y=room.centerY()+80; }
    }

    @Test void heldMovementStartsImmediatelyAndStopsOnRelease() {
        GameWorld w=world(); PlayerInput i=keys(); i.moveX=1;
        float x=w.hero.x;
        w.step(dt,i); assertEquals(b.hero.moveSpeed*dt,w.hero.x-x,0.02);
        step(w,i,23); assertEquals(b.hero.moveSpeed*dt*24,w.hero.x-x,0.05);
        i.moveX=0; x=w.hero.x; step(w,i,6);
        assertEquals(x,w.hero.x); assertFalse(w.hero.moving); assertEquals(0,w.hero.walkTime);
    }
    @Test void diagonalsHaveSameSpeed() {
        GameWorld w=world(); PlayerInput i=keys(); i.moveX=i.moveY=1;
        float x=w.hero.x,y=w.hero.y; step(w,i,6);
        assertEquals(b.hero.moveSpeed*dt*6,Math.hypot(w.hero.x-x,w.hero.y-y),0.03);
    }
    @Test void holdingAttackDoesNotHalveMovementSpeed() {
        GameWorld w=world(); PlayerInput i=keys(); i.moveX=1;i.attackHeld=true;
        float x=w.hero.x; step(w,i,24);
        assertTrue(w.hero.x-x>b.hero.moveSpeed*dt*24*0.90);
    }
    @Test void walkingAnimationDoesNotRunThroughWalls() {
        GameWorld w=world(); RoomDef room=w.room;
        w.hero.x=room.bounds.x+b.hero.bodyWidth/2f+0.01f; w.hero.y=room.bounds.y+32;
        PlayerInput i=keys();i.moveX=-1;step(w,i,30);
        assertFalse(w.hero.moving);assertEquals(0,w.hero.walkTime);
    }
    @Test void quickAttackTapIsNotLostBetweenFrames() {
        GameWorld w=world();PlayerInput i=keys();i.attackPressed=true;
        w.step(dt,i);assertNotNull(w.hero.attack);
    }
    @Test void bufferedAttackFiresAfterRecoveryButMenuClearCancelsIt() {
        GameWorld w=world();PlayerInput i=keys();i.attackHeld=true;w.step(dt,i);
        AttackInstance first=w.hero.attack;first.t=first.total()-0.08f;
        i.attackHeld=false;i.attackPressed=true;w.step(dt,i);i.clearEdges();
        step(w,i,6);assertNotSame(first,w.hero.attack);assertNotNull(w.hero.attack);
        w.hero.attack.t=w.hero.attack.total()-0.08f;i.attackPressed=true;w.step(dt,i);i.clearEdges();
        w.clearBufferedActions();step(w,i,7);assertNull(w.hero.attack);
    }
    @Test void swordCyclesThroughThreeDifferentSwings() {
        GameWorld w=world();PlayerInput i=keys();i.attackHeld=true;
        java.util.List<AttackInstance> attacks=new java.util.ArrayList<>();
        AttackInstance last=null;
        for(int f=0;f<120;f++) {w.step(dt,i); if(w.hero.attack!=null&&w.hero.attack!=last) {last=w.hero.attack; attacks.add(last);} }
        assertTrue(attacks.size()>=4);
        assertEquals(1,attacks.get(0).combo);assertEquals(2,attacks.get(1).combo);assertEquals(3,attacks.get(2).combo);
        assertEquals(Math.round(com.yongsa.model.DamageCalc.weaponDamage(b,"sword",0)*b.hero.finisherMultiplier),attacks.get(2).damage);assertEquals(1,attacks.get(3).combo);
    }
    @Test void autoAimSelectsVisibleEnemyAndEmptyRoomUsesMovement() {
        GameWorld w=world();PlayerInput i=keys();i.moveX=-1;w.step(dt,i);assertEquals(4,w.hero.aimDir);
        arena(w);i.moveX=0;
        Enemy target=w.combat.enemies.get(0);target.x=w.hero.x+80;target.y=w.hero.y;
        w.step(dt,i);assertSame(target,w.hero.target);assertEquals(0,w.hero.aimDir);
        target.x=w.combat.room.bounds.x-60;w.step(dt,i);assertNotSame(target,w.hero.target);
    }
    @Test void dodgeIsImmediateInvulnerableAndHasCooldown() {
        GameWorld w=world();PlayerInput i=keys();i.moveX=1;i.dodgePressed=true;
        float x=w.hero.x;w.step(dt,i);i.clearEdges();
        assertTrue(w.hero.x-x>b.hero.moveSpeed*dt*2);assertTrue(w.hero.dodgeTime>0);
        w.damageHero(20,w.hero.x-5,w.hero.y);assertEquals(100,w.hero.hp);
        step(w,i,13);assertEquals(0,w.hero.dodgeTime);
        i.dodgePressed=true;w.step(dt,i);assertEquals(0,w.hero.dodgeTime);
        // 구르기가 끝난 직후에도 잠깐은 다치지 않는다 (적을 지나친 자리에서 바로 맞지 않게)
        assertTrue(w.hero.dodgeSafe>0);w.damageHero(20,w.hero.x-5,w.hero.y);assertEquals(100,w.hero.hp);
        i.clearEdges();step(w,i,10);assertEquals(0,w.hero.dodgeSafe);
        w.damageHero(20,w.hero.x-5,w.hero.y);assertEquals(80,w.hero.hp);
    }
    @Test void dodgeCannotCrossLockedGate() {
        GameWorld w=world();DoorDef gate=TestData.gate("fortress");
        w.hero.x=gate.bounds.x-12;w.hero.y=gate.bounds.y+gate.bounds.height/2;
        PlayerInput i=keys();i.moveX=1;i.dodgePressed=true;step(w,i,18);
        assertTrue(w.hero.x+b.hero.bodyWidth/2<=gate.bounds.x+0.1);
    }
    @Test void shockwaveOriginStaysAtCastPointWhileWalking() {
        GameWorld w=world();w.state.defeatedBosses.add("boss_mine");
        PlayerInput i=keys();i.shockwavePressed=true;w.step(dt,i);i.clearEdges();step(w,i,8);
        AttackInstance a=w.hero.attack;assertTrue(a.originLocked);float x=a.originX;
        i.moveX=1;step(w,i,3);assertEquals(x,a.originX);assertTrue(w.hero.x>x);
    }
    @Test void menuKeyRepeatCannotCraftTwiceAndMouseIsConsumed() {
        int[] count={0};KeyboardInput keyboard=new KeyboardInput(k->{count[0]++;return true;});
        keyboard.keyDown(Input.Keys.J);keyboard.keyDown(Input.Keys.J);keyboard.keyDown(Input.Keys.J);
        assertEquals(1,count[0]);keyboard.keyUp(Input.Keys.J);keyboard.keyDown(Input.Keys.J);assertEquals(2,count[0]);
        assertTrue(keyboard.touchDown(20,20,0,0));assertEquals(2,count[0]);
    }
    @Test void swordShattersProjectilesAndDodgeAvoidsThem() {
        GameWorld w=world();arena(w);PlayerInput i=keys();
        Projectile p=new Projectile(w.hero.x+24,w.hero.hurtY(),(float)Math.PI,0,12);
        w.combat.projectiles.add(p);
        w.hero.attack=new AttackInstance(AttackInstance.Kind.NORMAL,0,0.001f,0.2f,0.1f,12,76,78);
        w.step(dt,i);assertTrue(w.combat.projectiles.isEmpty());assertEquals(100,w.hero.hp);
        w.combat.projectiles.add(new Projectile(w.hero.x,w.hero.hurtY(),0,0,12));
        i.dodgePressed=true;w.step(dt,i);assertEquals(100,w.hero.hp);
    }
    @Test void swordSectorMatchesCrescentAndCannotHitBehindHero() {
        HitShape s=HitShape.box(0,0,1,0,76,78);s.type=HitShape.Type.SECTOR;
        assertTrue(s.hitsCircle(50,35,3));
        assertFalse(s.hitsCircle(-30,0,3));
        assertFalse(s.hitsCircle(90,0,3));
        assertTrue(s.hitsCircle(76,0,3));
    }
    @Test void allThreeBossesCanBeDefeatedWithKeyboardCombatInputs() {
        String[] zones={"mine","fortress","temple"};int[] levels={3,6,10};
        // 보스마다 따로 싸운다 (앞 싸움의 결과가 다음 싸움에 섞이지 않게). 단순한 가상 플레이어라 시작 자리를
        // 조금씩 바꿔 일곱 번 싸우고, 그중 절반 넘게 이기면 "키보드만으로 깰 수 있다"고 본다.
        for(int n=0;n<zones.length;n++) {
            int wins=0;String log="";
            for(int trial=0;trial<7;trial++) {
                GameWorld w=world();
                for(int k=0;k<n;k++) w.state.defeatedBosses.add("boss_"+zones[k]);
                w.state.weapon("sword").level=levels[n];
                boolean won=fightBoss(w,zones[n],(trial-3)*25);
                if(won) wins++;
                log+=(won?"승":"패(체력 "+(w.combat==null?0:w.combat.bossEnemy().hp)+" 남음)")+" ";
            }
            assertTrue(wins>=4,zones[n]+" 가상 플레이어 전적: "+log);
        }
    }
    /** Scenario setup only: no enemy HP edits or artificial damage during the fight. */
    boolean fightBoss(GameWorld w,String zone,float offsetX) {
        PlayerInput i=keys();
        RoomDef room=w.map.room(zone+"_boss");
        w.hero.x=room.centerX()+offsetX;w.hero.y=room.centerY()-65;
        w.step(dt,keys());
        int frames=0;
        while(w.combat!=null&&!w.hero.dead&&frames++<60*90) {
            Enemy boss=w.combat.bossEnemy();
            float dx=boss.x-w.hero.x,dy=boss.y-w.hero.y;
            float distance=(float)Math.hypot(dx,dy);
            i.moveX=distance>64?dx:0;i.moveY=distance>64?dy:0;i.attackHeld=true;
            i.shockwavePressed=w.canShockwave();
            if(boss.state==Enemy.State.TELEGRAPH&&boss.telegraphTime-boss.stateTime<0.16f&&w.hero.dodgeCooldown<=0) {
                if("charge".equals(boss.attackId)) {i.moveX=-dy;i.moveY=dx;}
                else {i.moveX=-dx;i.moveY=-dy;}
                i.dodgePressed=true;
            }
            // Summoned minions: fight the nearest one first and sidestep its charge like a player would.
            for(Enemy m:w.combat.enemies) {
                if(m.isBoss()||!m.alive()) continue;
                float mx=m.x-w.hero.x,my=m.y-w.hero.y;
                if(m.contactMul()>0&&Math.hypot(mx,my)<distance) {distance=(float)Math.hypot(mx,my);i.moveX=distance>50?mx:0;i.moveY=distance>50?my:0;}
                if(m.dashing&&m.state==Enemy.State.TELEGRAPH&&m.telegraphTime-m.stateTime<0.16f&&Math.hypot(mx,my)<220&&w.hero.dodgeCooldown<=0) {
                    i.moveX=-my;i.moveY=mx;i.dodgePressed=true;
                }
            }
            // Roll through bullets that are about to connect (the bosses now fill the room with them).
            for(Projectile p:w.combat.projectiles) {
                float px=p.x-w.hero.hurtX(),py=p.y-w.hero.hurtY();
                boolean closing=px*p.dx+py*p.dy<0;
                if(closing&&Math.hypot(px,py)<46&&w.hero.dodgeCooldown<=0) {i.moveX=-p.dy;i.moveY=p.dx;i.dodgePressed=true;break;}
            }
            w.step(dt,i);i.clearEdges();w.events.clear();
        }
        return !w.hero.dead&&w.combat==null&&w.state.isBossDefeated("boss_"+zone);
    }
}
