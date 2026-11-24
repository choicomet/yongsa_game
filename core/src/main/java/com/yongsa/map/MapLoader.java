package com.yongsa.map;

import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 맵 그림을 미리 준비하는 일꾼 (렉 줄이기).
 * 큰 방 그림을 읽고 가장자리를 흐리는 계산은 한 장에 수백 ms 가 걸려서, 방에 들어설 때 하면 화면이 멈춘다.
 * 용사가 다가가는 동안 이 계산을 다른 스레드에서 미리 해 두고, 그리는 쪽은 다 된 결과(Pixmap)만 받아 올린다.
 * 그래픽(Texture) 만들기는 반드시 그리는 스레드에서 한다 — 여기서는 CPU 계산만 한다.
 */
final class MapLoader implements Disposable {
    /** 개발용: 그리는 스레드가 기다린 불러오기를 찍는다 (-Dyongsa.mapLoadLog=true). */
    private static final boolean LOG = Boolean.getBoolean("yongsa.mapLoadLog");
    private final ExecutorService pool;
    private final Map<String, Future<? extends Disposable>> jobs = new HashMap<>();
    /** 같은 프레임에 여러 장을 올리면 그 프레임이 길어지므로, 미리 올리기는 한 프레임에 이만큼만. */
    private int uploadsLeft;

    MapLoader() {
        int threads = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 1));
        pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "yongsa-map-loader");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    /** 아직 없으면 미리 계산을 맡긴다. */
    void request(String key, Callable<? extends Disposable> work) {
        jobs.computeIfAbsent(key, k -> pool.submit(work));
    }

    /** 맡긴 계산이 끝났는지. */
    boolean ready(String key) {
        Future<?> f = jobs.get(key);
        return f != null && f.isDone();
    }

    boolean pending(String key) {
        return jobs.containsKey(key);
    }

    /**
     * 결과를 받아 간다. 미리 맡긴 게 있으면 그것을 (덜 됐으면 기다린다), 없으면 지금 바로 계산한다.
     * 받은 결과의 정리(dispose)는 받는 쪽 책임이다.
     */
    @SuppressWarnings("unchecked")
    <T extends Disposable> T take(String key, Callable<T> work) {
        Future<? extends Disposable> f = jobs.remove(key);
        long start = LOG ? System.nanoTime() : 0;
        boolean waited = f == null || !f.isDone();
        try {
            T result = f != null ? (T) f.get() : work.call();
            if (LOG && waited) System.out.printf("MAPLOAD %s %s %.0f ms%n", f == null ? "sync" : "wait", key, (System.nanoTime() - start) / 1e6);
            return result;
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            throw new RuntimeException(c);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 이번 프레임에 미리 올리기를 한 장 더 해도 되는지 (되면 한 장 쓴 것으로 센다). */
    boolean spendUpload() {
        if (uploadsLeft <= 0) return false;
        uploadsLeft--;
        return true;
    }

    void newFrame(int uploads) {
        uploadsLeft = uploads;
    }

    /** 멀어져서 쓸 일이 없어진 미리 계산을 버린다 (끝난 것은 결과를 정리). */
    void drop(String key) {
        Future<? extends Disposable> f = jobs.remove(key);
        if (f == null) return;
        if (f.cancel(false)) return;
        if (f.isDone()) disposeResult(f);
        else pool.execute(() -> disposeResult(f));
    }

    /** 조건에 맞는 미리 계산을 모두 버린다. */
    void dropIf(java.util.function.Predicate<String> far) {
        for (String k : new java.util.ArrayList<>(jobs.keySet())) if (far.test(k)) drop(k);
    }

    private static void disposeResult(Future<? extends Disposable> f) {
        try {
            Disposable d = f.get();
            if (d != null) d.dispose();
        } catch (Exception ignored) {
            // 버리는 중의 실패는 무시한다.
        }
    }

    @Override
    public void dispose() {
        for (Future<? extends Disposable> f : jobs.values()) {
            if (f.cancel(false)) continue;
            if (f.isDone()) disposeResult(f);
            else pool.execute(() -> disposeResult(f));
        }
        jobs.clear();
        pool.shutdown();
    }
}
