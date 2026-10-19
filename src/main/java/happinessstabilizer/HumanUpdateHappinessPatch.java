package happinessstabilizer;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.settler.SettlerThoughtStacks;
import java.lang.reflect.Field;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target = HumanMob.class, name = "updateHappiness", arguments = {})
public class HumanUpdateHappinessPatch {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(@Advice.This HumanMob human) {
        try {
            // Need to use reflection to get the private settlerHappiness field
            Field happinessField;
            happinessField = HumanMob.class.getDeclaredField("settlerHappiness");
            happinessField.setAccessible(true);

            // Now prepare to calculate happiness
            int oldHappiness = human.getSettlerHappiness();
            int newScore = 0; // use this variable instead of updating settlerHappiness directly as in vanilla
            // Otherwise all the work here is vanilla until otherwise noted

            for(SettlerThoughtStacks thought : human.getStaticThoughts()) {
                newScore += thought.getHappinessModifier(human);
            }

            // I don't know why this is here, but our changes don't seem to affect it at all so whatever
            if (human.isServer()) {
                human.getRomancePersonality().updateRelationship(human.getServer(), false);
            }

            newScore += human.getActiveThoughts().getTotalHappinessModifier();

            // This block is all for Cloud Nine achievement
            if (oldHappiness != newScore) {
                human.sendMovementPacket(false);
                if (human.getSettlerQualityOfLife() >= 6) {
                    NetworkSettlementData networkData = human.getSettlerSettlementNetworkData();
                    if (networkData != null) {
                        // Had to restructure this to replace lambda expression with conventional for loop
                        for (ServerClient c : networkData.getTeamMembers()) {
                            if (c.achievementsLoaded()) {
                                c.achievements().CLOUD_NINE.markCompleted(c);
                            }
                        }
                    }
                }
            }

            // Here's our actual change: lerp between the previous Happiness and the freshly calculated score
            int stabilizedHappiness = Math.round(0.75F * oldHappiness + 0.25F * newScore);
            happinessField.set(human, stabilizedHappiness);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        // Skip the original method body
        return true;
    }
}