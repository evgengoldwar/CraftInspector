package craftinspector.history;

/**
 * Implemented by AE2's {@code CraftingCPUCluster} through a mixin.
 */
public interface ICraftTrackerHolder {

    /**
     * @return statistics of the order currently running on this CPU, or null if the order is not tracked
     */
    CraftRecord craftinspector$getTracker();

    /**
     * @return AE2's crafting timer of the current order in nanoseconds
     */
    long craftinspector$getElapsed();
}
