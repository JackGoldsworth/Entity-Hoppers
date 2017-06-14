package com.wynprice.entityHoppers.hopper;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.block.Block;
import net.minecraft.block.BlockHopper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.IHopper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public class InventoryCodeHooks 
{
	/**
     * Copied from TileEntityHopper#captureDroppedItems and added capability support
     * @return Null if we did nothing {no IItemHandler}, True if we moved an item, False if we moved no items
     */
    @Nullable
    public static Boolean extractHook(IHopper dest)
    {
        Pair<IItemHandler, Object> itemHandlerResult = getItemHandler(dest, EnumFacing.UP);
        if (itemHandlerResult == null)
            return null;

        IItemHandler handler = itemHandlerResult.getKey();

        for (int i = 0; i < handler.getSlots(); i++)
        {
            ItemStack extractItem = handler.extractItem(i, 1, true);
            if (!extractItem.isEmpty())
            {
                for (int j = 0; j < dest.getSizeInventory(); j++)
                {
                    ItemStack destStack = dest.getStackInSlot(j);
                    if (dest.isItemValidForSlot(j, extractItem) && (destStack.isEmpty() || destStack.getCount() < destStack.getMaxStackSize() && destStack.getCount() < dest.getInventoryStackLimit() && ItemHandlerHelper.canItemStacksStack(extractItem, destStack)))
                    {
                        extractItem = handler.extractItem(i, 1, false);
                        if (destStack.isEmpty())
                            dest.setInventorySlotContents(j, extractItem);
                        else
                        {
                            destStack.grow(1);
                            dest.setInventorySlotContents(j, destStack);
                        }
                        dest.markDirty();
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Copied from TileEntityHopper#transferItemsOut and added capability support
     */
    public static boolean insertHook(TileEntityEntityHopper hopper)
    {
        EnumFacing hopperFacing = BlockHopper.getFacing(hopper.getBlockMetadata());
        Pair<IItemHandler, Object> destinationResult = getItemHandler(hopper, hopperFacing);
        if (destinationResult == null)
        {
            return false;
        }
        else
        {
            IItemHandler itemHandler = destinationResult.getKey();
            Object destination = destinationResult.getValue();
            if (isFull(itemHandler))
            {
                return false;
            }
            else
            {
                for (int i = 0; i < hopper.getSizeInventory(); ++i)
                {
                    if (!hopper.getStackInSlot(i).isEmpty())
                    {
                        ItemStack originalSlotContents = hopper.getStackInSlot(i).copy();
                        ItemStack insertStack = hopper.decrStackSize(i, 1);
                        ItemStack remainder = putStackInInventoryAllSlots(hopper, destination, itemHandler, insertStack);

                        if (remainder.isEmpty())
                        {
                            return true;
                        }

                        hopper.setInventorySlotContents(i, originalSlotContents);
                    }
                }

                return false;
            }
        }
    }

    private static ItemStack putStackInInventoryAllSlots(TileEntity source, Object destination, IItemHandler destInventory, ItemStack stack)
    {
        for (int slot = 0; slot < destInventory.getSlots() && !stack.isEmpty(); slot++)
        {
            stack = insertStack(source, destination, destInventory, stack, slot);
        }
        return stack;
    }

    /**
     * Copied from TileEntityHopper#insertStack and added capability support
     */
    private static ItemStack insertStack(TileEntity source, Object destination, IItemHandler destInventory, ItemStack stack, int slot)
    {
        ItemStack itemstack = destInventory.getStackInSlot(slot);

        if (destInventory.insertItem(slot, stack, true).isEmpty())
        {
            boolean insertedItem = false;
            boolean inventoryWasEmpty = isEmpty(destInventory);

            if (itemstack.isEmpty())
            {
                destInventory.insertItem(slot, stack, false);
                stack = ItemStack.EMPTY;
                insertedItem = true;
            }
            else if (ItemHandlerHelper.canItemStacksStack(itemstack, stack))
            {
                int originalSize = stack.getCount();
                stack = destInventory.insertItem(slot, stack, false);
                insertedItem = originalSize < stack.getCount();
            }

            if (insertedItem)
            {
                if (inventoryWasEmpty && destination instanceof TileEntityEntityHopper)
                {
                    TileEntityEntityHopper destinationHopper = (TileEntityEntityHopper)destination;

                    if (!destinationHopper.mayTransfer())
                    {
                        int k = 0;

                        if (source instanceof TileEntityEntityHopper)
                        {
                            if (destinationHopper.getLastUpdateTime() >= ((TileEntityEntityHopper) source).getLastUpdateTime())
                            {
                                k = 1;
                            }
                        }

                        destinationHopper.setTransferCooldown(8 - k);
                    }
                }
            }
        }

        return stack;
    }

    @Nullable
    private static Pair<IItemHandler, Object> getItemHandler(IHopper hopper, EnumFacing hopperFacing)
    {
        double x = hopper.getXPos() + (double) hopperFacing.getFrontOffsetX();
        double y = hopper.getYPos() + (double) hopperFacing.getFrontOffsetY();
        double z = hopper.getZPos() + (double) hopperFacing.getFrontOffsetZ();
        return getItemHandler(hopper.getWorld(), x, y, z, hopperFacing.getOpposite());
    }

    private static boolean isFull(IItemHandler itemHandler)
    {
        for (int slot = 0; slot < itemHandler.getSlots(); slot++)
        {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.isEmpty() || stackInSlot.getCount() != stackInSlot.getMaxStackSize())
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmpty(IItemHandler itemHandler)
    {
        for (int slot = 0; slot < itemHandler.getSlots(); slot++)
        {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.getCount() > 0)
            {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static Pair<IItemHandler, Object> getItemHandler(World worldIn, double x, double y, double z, final EnumFacing side)
    {
        Pair<IItemHandler, Object> destination = null;
        int i = MathHelper.floor(x);
        int j = MathHelper.floor(y);
        int k = MathHelper.floor(z);
        BlockPos blockpos = new BlockPos(i, j, k);
        net.minecraft.block.state.IBlockState state = worldIn.getBlockState(blockpos);
        Block block = state.getBlock();

        if (block.hasTileEntity(state))
        {
            TileEntity tileentity = worldIn.getTileEntity(blockpos);
            if (tileentity != null)
            {
                if (tileentity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side))
                {
                    IItemHandler capability = tileentity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
                    destination = ImmutablePair.<IItemHandler, Object>of(capability, tileentity);
                }
            }
        }

        return destination;
    }
}