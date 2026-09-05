package mcjty.lostcities.worldgen.highway;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public final class HighwayPlanningService implements AutoCloseable {
    private final IntercityHighwayPlanner planner;
    private final Executor executor;

    private final ConcurrentHashMap<HubKey, CompletableFuture<Optional<HighwayHub>>> hubFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<HubKey, CompletableFuture<List<IntercityHighwayPlanner.ConnectionCandidate>>> candidateFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<HubKey, CompletableFuture<List<HubKey>>> selectionFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<HubKey, CompletableFuture<List<HighwayRoute>>> routeFutures = new ConcurrentHashMap<>();

    private final LongAdder runningTasks = new LongAdder();
    private final LongAdder completedTasks = new LongAdder();
    private final LongAdder submittedTasks = new LongAdder();

    public HighwayPlanningService(IntercityHighwayPlanner planner) {
        this.planner = planner;
        this.executor = new ForkJoinPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    }

    public CompletableFuture<Optional<HighwayHub>> getHub(HubKey key) {
        return hubFutures.computeIfAbsent(key, this::scheduleHub);
    }

    private CompletableFuture<Optional<HighwayHub>> scheduleHub(HubKey key) {
        return submit(() -> planner.getHub(key));
    }

    public HighwayInfo getHighwayInfo(int chunkX, int chunkZ) {
        return planner.getHighwayInfo(chunkX, chunkZ);
    }

    public CompletableFuture<Void> prepare(int chunkX, int chunkZ) {
        HubKey center = planner.getPlanningCell(chunkX, chunkZ);
        int routeRadius = planner.settings().hubSearchRadiusCells();
        int hubRadius = routeRadius * 2;
        List<CompletableFuture<Optional<HighwayHub>>> hubFutures = new ArrayList<>();

        for (int dx = -hubRadius; dx <= hubRadius; dx++) {
            for (int dz = -hubRadius; dz <= hubRadius; dz++) {
                HubKey key = new HubKey(center.planningCellX() + dx, center.planningCellZ() + dz);
                hubFutures.add(getHub(key));
            }
        }

        CompletableFuture<Void> hubsReady = CompletableFuture.allOf(hubFutures.toArray(CompletableFuture[]::new));
        List<CompletableFuture<List<HighwayRoute>>> routes = new ArrayList<>();

        for (int dx = -routeRadius; dx <= routeRadius; dx++) {
            for (int dz = -routeRadius; dz <= routeRadius; dz++) {
                HubKey owner = new HubKey(center.planningCellX() + dx, center.planningCellZ() + dz);
                routes.add(hubsReady.thenCompose(ignored -> getOwnedRoutes(owner)));
            }
        }

        return CompletableFuture.allOf(routes.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<List<HighwayHub>> prepareHubs(int chunkX, int chunkZ) {
        HubKey center = planner.getPlanningCell(chunkX, chunkZ);
        int radius = planner.settings().hubSearchRadiusCells();

        List<CompletableFuture<Optional<HighwayHub>>> futures = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                HubKey key = new HubKey(center.planningCellX() + dx, center.planningCellZ() + dz);
                futures.add(getHub(key));
            }
        }

        CompletableFuture<?>[] all = futures.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(all)
                .thenApply(ignored -> futures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Optional::stream)
                        .sorted()
                        .toList());
    }

    public CompletableFuture<List<IntercityHighwayPlanner.ConnectionCandidate>> getCandidates(HubKey source) {
        return candidateFutures.computeIfAbsent(
                source,
                key -> getHub(key).thenApplyAsync(
                        ignored -> planner.getConnectionCandidates(key),
                        executor
                )
        );
    }

    public CompletableFuture<List<HubKey>> getSelectedNeighbours(HubKey source) {
        return selectionFutures.computeIfAbsent(
                source,
                key -> getCandidates(key).thenApplyAsync(
                        ignored -> planner.getSelectedNeighbours(key),
                        executor
                )
        );
    }

    public CompletableFuture<List<HighwayRoute>> getOwnedRoutes(HubKey owner) {
        return routeFutures.computeIfAbsent(
                owner,
                key -> getSelectedNeighbours(key).thenApplyAsync(
                        ignored -> planner.getOwnedRoutes(key),
                        executor
                )
        );
    }

    private <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        submittedTasks.increment();

        return CompletableFuture.supplyAsync(() -> {
            runningTasks.increment();
            try {
                return supplier.get();
            } finally {
                runningTasks.decrement();
                completedTasks.increment();
            }
        }, executor);
    }

    public void clear() {
        hubFutures.clear();
        candidateFutures.clear();
        selectionFutures.clear();
        routeFutures.clear();

        planner.clearCaches();
    }

    @Override
    public void close() {
        if (executor instanceof ForkJoinPool pool) {
            pool.shutdown();
        }
    }

    public String getParallelismStats() {
        if (executor instanceof ForkJoinPool pool) {
            return "Parallelism: " + pool.getParallelism()
                    + ", active: " + pool.getActiveThreadCount()
                    + ", running: " + pool.getRunningThreadCount()
                    + ", queued: " + pool.getQueuedTaskCount()
                    + ", submissions: " + pool.getQueuedSubmissionCount()
                    + ", runningTasks: " + runningTasks.sum()
                    + ", completedTasks: " + completedTasks.sum()
                    + ", submittedTasks: " + submittedTasks.sum();
        }

        return "Running tasks: " + runningTasks.sum()
                + ", completed tasks: " + completedTasks.sum()
                + ", submitted tasks: " + submittedTasks.sum();
    }
}