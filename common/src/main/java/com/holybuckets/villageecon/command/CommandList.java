package com.holybuckets.villageecon.command;

//Project imports

import com.holybuckets.foundation.event.CommandRegistry;
import com.holybuckets.villageecon.LoggerProject;
import com.holybuckets.villageecon.core.VillageManager;
import com.holybuckets.villageecon.core.debug.VillageEconTradeSimulator;
import com.holybuckets.villageecon.core.model.VillageEconomyChunk;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class CommandList {

    public static final String CLASS_ID = "026";
    private static final String PREFIX = "hbVillageEcon";

    public static void register() {
        CommandRegistry.register(VillageStatus::noArgs);
        CommandRegistry.register(ListVillages::noArgs);
        CommandRegistry.register(SimulateSales::noArgs);
        CommandRegistry.register(SimulateSales::limitCount);
        CommandRegistry.register(SimulateSales::limitCountSpecifyResource);
        CommandRegistry.register(SimulateAuto::toggle);
        CommandRegistry.register(SimulateAuto::toggleSpecifyResource);
        CommandRegistry.register(SimulateVolatility::setValue);
        CommandRegistry.register(StockVillage::setStacks);
        CommandRegistry.register(RunProcess::tick);
        CommandRegistry.register(RunProcess::day);
        CommandRegistry.register(RunProcess::cycle);
        CommandRegistry.register(CreateMayor::atPlayer);
    }

    private static void reply(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    //1. Village Status
    private static class VillageStatus
    {
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("status")
                    .executes(context -> execute(context.getSource()))
                );
        }

        private static int execute(CommandSourceStack source)
        {
            LoggerProject.logDebug(CLASS_ID + "001", "Village Status Command");
            reply(source, VillageEconTradeSimulator.status(source.getLevel()));
            return 1;
        }
    }
    //END COMMAND

    //2. List Villages
    private static class ListVillages
    {
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("listVillages")
                    .executes(context -> execute(context.getSource()))
                );
        }

        private static int execute(CommandSourceStack source)
        {
            LoggerProject.logDebug(CLASS_ID + "002", "List Villages Command");
            reply(source, VillageEconTradeSimulator.listVillages(source.getLevel()));
            return 1;
        }
    }
    //END COMMAND

    //3. Simulate Sales
    private static class SimulateSales
    {
        private static LiteralArgumentBuilder<CommandSourceStack> noArgs() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("simulateSales")
                    .executes(context -> execute(context.getSource(), 1, null))
                );
        }

        private static LiteralArgumentBuilder<CommandSourceStack> limitCount() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("simulateSales")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int count = IntegerArgumentType.getInteger(context, "count");
                            return execute(context.getSource(), count, null);
                        })
                    )
                );
        }

        private static LiteralArgumentBuilder<CommandSourceStack> limitCountSpecifyResource() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("simulateSales")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .then(Commands.argument("resource", StringArgumentType.string())
                            .executes(context -> {
                                int count = IntegerArgumentType.getInteger(context, "count");
                                String resource = StringArgumentType.getString(context, "resource");
                                return execute(context.getSource(), count, resource);
                            })
                        )
                    )
                );
        }

        private static int execute(CommandSourceStack source, int count, String resourceId)
        {
            LoggerProject.logDebug(CLASS_ID + "003", "Simulate Sales Command");
            ServerLevel level = source.getLevel();
            int logged = VillageEconTradeSimulator.logDummySales(level, resourceId, count);

            if (logged == 0) {
                source.sendFailure(Component.literal(
                    "No sales logged. Check a village is loaded and the resource id is valid."));
                return 0;
            }

            reply(source, "Logged " + logged + " dummy sale(s)"
                + (resourceId == null ? "" : " for " + resourceId));
            return logged;
        }
    }
    //END COMMAND

    //4. Auto Simulate
    private static class SimulateAuto
    {
        private static LiteralArgumentBuilder<CommandSourceStack> toggle() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("simulateAuto")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                            return execute(context.getSource(), enabled, null);
                        })
                    )
                );
        }

        private static LiteralArgumentBuilder<CommandSourceStack> toggleSpecifyResource() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("simulateAuto")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .then(Commands.argument("resource", StringArgumentType.string())
                            .executes(context -> {
                                boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                String resource = StringArgumentType.getString(context, "resource");
                                return execute(context.getSource(), enabled, resource);
                            })
                        )
                    )
                );
        }

        private static int execute(CommandSourceStack source, boolean enabled, String resourceId)
        {
            LoggerProject.logDebug(CLASS_ID + "004", "Simulate Auto Command");
            VillageEconTradeSimulator.setAutoSimulate(enabled);
            if (resourceId != null) VillageEconTradeSimulator.setAutoResourceId(resourceId);

            reply(source, "Auto sale simulation " + (enabled ? "enabled" : "disabled")
                + " for " + (VillageEconTradeSimulator.getAutoResourceId() == null
                    ? "the first active resource" : VillageEconTradeSimulator.getAutoResourceId()));
            return 1;
        }
    }
    //END COMMAND

    //5. Simulate Volatility
    private static class SimulateVolatility
    {
        private static LiteralArgumentBuilder<CommandSourceStack> setValue() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("simulateVolatility")
                    .then(Commands.argument("volatility", FloatArgumentType.floatArg(0f, 4f))
                        .executes(context -> {
                            float volatility = FloatArgumentType.getFloat(context, "volatility");
                            return execute(context.getSource(), volatility);
                        })
                    )
                );
        }

        private static int execute(CommandSourceStack source, float volatility)
        {
            LoggerProject.logDebug(CLASS_ID + "005", "Simulate Volatility Command");
            VillageEconTradeSimulator.setVolatility(volatility);
            reply(source, "Sale price volatility set to " + VillageEconTradeSimulator.getVolatility());
            return 1;
        }
    }
    //END COMMAND

    //6. Stock Village
    private static class StockVillage
    {
        private static LiteralArgumentBuilder<CommandSourceStack> setStacks() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("stockVillage")
                    .then(Commands.argument("stacks", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            int stacks = IntegerArgumentType.getInteger(context, "stacks");
                            return execute(context.getSource(), stacks);
                        })
                    )
                );
        }

        private static int execute(CommandSourceStack source, int stacks)
        {
            LoggerProject.logDebug(CLASS_ID + "006", "Stock Village Command");
            int stocked = VillageEconTradeSimulator.stockVillage(source.getLevel(), stacks);

            if (stocked == 0) {
                source.sendFailure(Component.literal("No village loaded to stock"));
                return 0;
            }

            reply(source, "Set " + stocked + " resource(s) to " + stacks + " stacks");
            return stocked;
        }
    }
    //END COMMAND

    //7. Run Process
    private static class RunProcess
    {
        private static LiteralArgumentBuilder<CommandSourceStack> tick() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("runTick")
                    .executes(context -> execute(context.getSource(), "tick"))
                );
        }

        private static LiteralArgumentBuilder<CommandSourceStack> day() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("runDay")
                    .executes(context -> execute(context.getSource(), "day"))
                );
        }

        private static LiteralArgumentBuilder<CommandSourceStack> cycle() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("runCycle")
                    .executes(context -> execute(context.getSource(), "cycle"))
                );
        }

        private static int execute(CommandSourceStack source, String process)
        {
            LoggerProject.logDebug(CLASS_ID + "007", "Run Process Command: " + process);
            ServerLevel level = source.getLevel();

            boolean ran = switch (process) {
                case "day" -> VillageEconTradeSimulator.runDailyProcess(level);
                case "cycle" -> VillageEconTradeSimulator.runCycleProcess(level);
                default -> VillageEconTradeSimulator.runTickProcess(level);
            };

            if (!ran) {
                source.sendFailure(Component.literal("No village loaded to run " + process + " process"));
                return 0;
            }

            reply(source, "Ran " + process + " process");
            return 1;
        }
    }
    //END COMMAND

    //8. Create Mayor
    private static class CreateMayor
    {
        private static LiteralArgumentBuilder<CommandSourceStack> atPlayer() {
            return Commands.literal(PREFIX)
                .then(Commands.literal("createMayor")
                    .executes(context -> execute(context.getSource()))
                );
        }

        private static int execute(CommandSourceStack source)
        {
            LoggerProject.logDebug(CLASS_ID + "008", "Create Mayor Command");
            ServerLevel level = source.getLevel();
            BlockPos origin = BlockPos.containing(source.getPosition());

            VillageEconomyChunk village = VillageManager.designateVillage(level, origin);

            if (village == null) {
                source.sendFailure(Component.literal(
                    "Could not designate a village here. This chunk may already be a village."));
                return 0;
            }

            reply(source, "Designated village " + village.getId()
                + " and spawned its mayor at " + origin.toShortString());
            return 1;
        }
    }
    //END COMMAND


}
//END CLASS COMMANDLIST
