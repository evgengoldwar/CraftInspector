package craftinspector.client.gui;

/**
 * Implemented by AE2's crafting status GUI through a mixin.
 */
public interface ICraftStatusGui {

    void craftinspector$setState(boolean enabled, int records);
}
