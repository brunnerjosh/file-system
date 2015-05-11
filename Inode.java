/**
 Inode Class


 @file Inode.java
 @author Jonathan, part of team Greg and The Gang
 @section 430 Final Project
 @date June 4, 2014
*/

 public class Inode {
   private final static int iNodeSize = 32;       // fix to 32 bytes
   public final static int directSize = 11;      // # direct pointers
   private final static int maxBytes = 512;

   public int length;                             // file size in bytes
   public short count;                            // # file-table entries pointing to this
   public short flag;                             // 0 = unused, 1 = used, ...
   public short direct[] = new short[directSize]; // direct pointers
   public short indirect;                         // a indirect pointer

   /*************************************************************************
    * Inode() constructor:
    *************************************************************************/
   Inode( ) {                                     // a default constructor
      length = 0;
      count = 0;
      flag = 1;
      for ( int i = 0; i < directSize; i++ )
         direct[i] = -1;
      indirect = -1;
   }

   /*************************************************************************
    * Inode(short) constructor: 
    *
    * Figures out how many blocks needed. Reads amount of blocks from disk.
    * Initializes data members with buffer size corresponding to the size of
    * each data type. This includes the length, count, flag, and the indirect
    * and direct pointers. The blkNumber is calculated by taking the format
    * of 16 inodes per superblock and then adding one to reach the next
    * superblock.
    *************************************************************************/
   Inode( short iNumber ) {      // retrieving inode from disk
      // design it by yourself.
      // figure out how many blocks to use by the inode (file) amount
      int blkNumber = 1 + iNumber / 16; //blocks are formatted by size of 16
      byte[] data = new byte[maxBytes];
      SysLib.rawread(blkNumber,data);

      //figure out how much to offset the initialize by getting the number
      //of blocks and then multiply by the size of an inode
      int offset = (iNumber % 16) * iNodeSize;

      //create space for data members
      length = SysLib.bytes2int(data,offset);
      offset +=4; //offset by 4 for int
      count = SysLib.bytes2short(data,offset);
      offset +=2; //offset by 2 for shorts
      flag = SysLib.bytes2short(data,offset);
      offset +=2;

      //allocate space for pointers
      for (int i = 0; i < directSize; i++) {
         direct[i] = SysLib.bytes2short(data,offset);
         offset +=2;
      }
      indirect = SysLib.bytes2short(data,offset);
      offset +=2;
   }

   /*************************************************************************
    * toDisk:
    *
    * Write back inode contents to disk. This includes the length, count,
    * flag, direct[], and indirect. Information is saved to the iNumber inode
    * in the disk.
    *************************************************************************/
   void toDisk( short iNumber ) {   // save to disk as the i-th inode
      // initialize buffer size
      byte[] data = new byte[iNodeSize];

      int offset = 0;

      SysLib.int2bytes(length, data, offset);
      offset +=4; //offset by 4 for int
      SysLib.short2bytes(count, data, offset);
      offset +=2; //offset by 2 for shorts
      SysLib.short2bytes(flag, data, offset);
      offset +=2;

      //allocate space for pointers
      for (int i = 0; i < directSize; i++) {
         SysLib.short2bytes(direct[i], data, offset);
         offset +=2;
      }
      SysLib.short2bytes(indirect, data, offset);
      offset +=2;

      int blkNumber = 1 + iNumber / 16; 
      byte[] newData = new byte[maxBytes];
      SysLib.rawread(blkNumber,newData);

      offset = (iNumber % 16) * iNodeSize; //same process as constructor

      //copy all of iNodeSize of data into newData array
      System.arraycopy(data, 0, newData, offset, iNodeSize);
      //now write that newData to disk at offset bit
      SysLib.rawwrite(blkNumber,newData);
   }
   
   /*************************************************************************
    * getIndexBlockNumber:
    *
    * Run through direct and indirect ptrs to block and read data if ptr
    * returns valid. else it will return error code.
    * IndexBlockNumber return values:
    *  0 = unused
    * -1 = error on write to used block
    * -2 = error on write to unused block
    * -3 = error on write to null pointer
    *************************************************************************/
   int getIndexBlockNumber(int entry, short offset){
    int target = entry / maxBytes;

    if (target < directSize){
      if (direct[target] >= 0){
        return -1;
      }
      //check if direct pointer is pointing to
      if((target > 0) && (direct[(target - 1)] == -1)){
        return -2;
      }
      direct[target] = offset;
      return 0; //unused
    }

    if (indirect < 0){
      return -3;
    }
    else{

      byte[] data = new byte[maxBytes];
      SysLib.rawread(indirect,data);

      int blockSpace = (target - directSize) * 2;
      if (SysLib.bytes2short(data, blockSpace) > 0){
        return -1;
      }
      else{
        SysLib.short2bytes(offset, data, blockSpace);
        SysLib.rawwrite(indirect, data);
      }
    }
    return 0; //unused
   }

   /*************************************************************************
    * setIndexBlock: 
    *
    * If index block indirect pointer is not set to -1, or if all the
    * direct pointers are = -1 then return false. Else the indirect pointer
    * will point to the indexBlockNumber passed, and data will be written.
    * Returns true if Else is the case.
    *************************************************************************/
   boolean setIndexBlock(short indexBlockNumber){
    // check pointers
    for (int i = 0; i < directSize; i++){ //direct pointers
      if (direct[i] == -1)
        return false;
    }
    if (indirect != -1)
      return false;

    indirect = indexBlockNumber;
    byte[] data = new byte[maxBytes];

    for (int i = 0; i < (maxBytes/2); i++){
        SysLib.short2bytes((short) -1, data, i*2);
    }
    SysLib.rawwrite(indexBlockNumber, data);

    return true;
   }

   /*************************************************************************
    * findTargetBlock:
    *
    * If the target is < 11 then return target block of direct pointer.
    * If the indirect pointer is < 0 then return the value of the index -1.
    * Else, write byte data using bytes2short method at the block space
    * calculated by taking the target block - the directSize space.
    * Then multiply by 2 for size of .
    *************************************************************************/
   int findTargetBlock(int offset){ 
      int target = offset / maxBytes;

      if (target < directSize)
        return direct[target];

      if (indirect < 0)
        return -1;

      byte[] data = new byte[maxBytes];
      SysLib.rawread(indirect, data);

      int blockSpace = (target - directSize) *2;
      return SysLib.bytes2short(data, blockSpace);
   }

    /*************************************************************************
    * removeIndexBlock:
    *
    * 
    * Reads data from indirect pointer unless pointer is value -1, then
    * return null. The data is returned to the FileSystem to deallocate block.
    *************************************************************************/
    byte[] freeIndirectBlock()
    {
      if (indirect >= 0) {
        byte[] data = new byte[maxBytes];
        SysLib.rawread(indirect, data);
        indirect = -1;
        return data;
      }
      else
        return null; //nothing to free
    }
} //end Inode.java

