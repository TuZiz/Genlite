package cn.genlite.platform;

import org.bukkit.entity.Player;
import cn.genlite.domain.ShellCredentials;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Runs on the player scheduler after durable unpack succeeds; never touches unrelated items. */
final class CreativeShellItems {
    private CreativeShellItems() {}
    static void consume(Player player, CraftItems items, UUID source, UUID token) {
        var inventory=player.getInventory();
        // Creative can clone a whole stack or move it while the transaction is pending.
        // Every copy of this exact spent credential is invalid, including the off-hand copy.
        int cursorSlot=inventory.getSize();
        consume(cursorSlot+1,
                slot -> items.credentials(slot==cursorSlot?player.getItemOnCursor():inventory.getItem(slot)),
                slot -> {if(slot==cursorSlot) player.setItemOnCursor(null);else inventory.setItem(slot,null);},source,token);
    }
    /** Synchronous inventory walk; reads the current slot immediately before changing that slot. */
    static void consume(int size, IntFunction<ShellCredentials> read, IntConsumer clear, UUID source, UUID token) {
        for(int slot=0;slot<size;slot++) {
            if(read.apply(slot).matches(source,token)) clear.accept(slot);
        }
    }
}
