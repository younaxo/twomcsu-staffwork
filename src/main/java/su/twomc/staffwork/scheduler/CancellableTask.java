package su.twomc.staffwork.scheduler;

/** Универсальная ручка отмены запланированной задачи — скрывает разницу между Bukkit- и Paper/Folia-планировщиками. */
public interface CancellableTask {

    void cancel();
}
