/*File SuberBlock.java
Written by Greg White
CSS430 Final Project Spring '14
Greg And The Gang*/

// Class SuperBlock
// The SuperBlock class is a component of the file system implemented in the CSS430
// final prject in ThreadOS. SuperBlock reads the physical SuperBlock from the disk,
// validates the health of the disk and provides methods for identifying free blocks,
// adding blocks to the free list, and writing back to disk the conents of SuperBlock.
public class SuperBlock{
	private final int defaultInodeBlocks = 64;
	private final int totalBlockLocation = 0;
	private final int totalInodeLocation = 4;
	private final int freeListLocation = 8;
	private final int defaultBlocks = 1000;

	public int totalBlocks; // the number of disk blocks
    public int totalInodes; // the number of inodes
    public int freeList;    // the block number of the free list's head

    //!!!FOR TESTING ONLY, SYNCING NAMES WITH CURRENT IMPLEMENTATION
    public int inodeBlocks;

    // SuperBlock Constructor
    // Public constructor for SuperBlock accepts a single int argument equal to the total
    // number of blocks on the Disk. The constructor will read the SuperBlock from disk and
    // intialize member variables for the number of blocks, the number of inodes, and the
    // block number of the free list's head. The constructor for SuperBlock is taken from
    // the CSS430 Final Project PDF with permission.
	public SuperBlock(int numBlocks){
		//read sblock from Disk 	!!!NEEDS PUBLIC PROPERTY OF DISK CALLED BLOCKSIZE (CAMEL CASE)
		byte[] superBlock = new byte[Disk.blockSize];
		//superblock always located in block zero
		SysLib.rawread(0, superBlock);

		//read total number of blocks
		totalBlocks = SysLib.bytes2int(superBlock,totalBlockLocation);
		//read total number of inodes
		totalInodes = SysLib.bytes2int(superBlock,totalInodeLocation);
		//read free list
		freeList = SysLib.bytes2int(superBlock,freeListLocation);

		//!!!FOR TESTING ONLY
		inodeBlocks = totalInodes;

		//validate disk contents
		if(totalBlocks == numBlocks && totalInodes > 0 && freeList >= 2){
			//valid disk
			return;
		}
		else{
			//SysLib.cout("INVALID DISK DOIN THE FORMAT");
			//disk is invalid, format required
			totalBlocks = numBlocks;
			format(defaultInodeBlocks);
		}
	}

	// Sync Method
	// The Sync method brings the physical SuperBlock contents (at block zero on disk) in line
	// with any updates performed to the SuperBlock class instance. Sync will write back to disk
	// the total number of blocks, the total number of inodes, and the free list.
	public void sync(){
		//create fresh block to hold superblock data
		byte[] newSuper = new byte[Disk.blockSize];
		
		//write total number of blocks to new super
		SysLib.int2bytes(totalBlocks,newSuper,totalBlockLocation);
		//write total number of inodes to new super
		SysLib.int2bytes(totalInodes,newSuper,totalInodeLocation);
		//write free list to new super
		SysLib.int2bytes(freeList,newSuper,freeListLocation);

		//write new super to disk
		SysLib.rawwrite(0,newSuper);
	}

	// getFreeBlock Method
	// The getFreeBlock method returns the first free block from the free list. The free block is the
	// top block from the free queue and is returned as an integer value. If there is an error 
	// (specifically, the absence of free blocks) -1 is returned to signify the operation failed.
	public int getFreeBlock(){
		//validate that the freeList head is within range   !!!MAY BE <= totalBlocks
		if(freeList > 0 && freeList < totalBlocks){
			//create dummy block to hold first free block
			byte[] freeBlock = new byte[Disk.blockSize];

			//read the free block from disk
			SysLib.rawread(freeList, freeBlock);

			//hold the free block location in dummy var
			int temp = freeList;

			//update next free block
			freeList = SysLib.bytes2int(freeBlock, 0);

			//return free block location
			return temp;	
		}
		
		//invalid freeList state, return -1
		return -1;
	} 

	// returnBlock Method
	// The returnBlock method attempts to add a newly freed block back to the free list. The newly freed
	// block is added to the end of the free block queue which operates, by definition, as FIFO. If the
	// freed block does not conform to the actual disk parameters held in SuperBlock, the operation fails
	// and returns false.
	public boolean returnBlock(int blockNumber){
		//validate that the returned block is within range   !!!MAY BE <= totalBlocks
		if(blockNumber > 0 && blockNumber < totalBlocks){
			//dummy variables to hold next free block and second walking pointer
			int nextFree = freeList;
			int temp = 0;

			//temp byte array to hold working block
			byte[] nextBlock = new byte[Disk.blockSize];

			//new block returned to free
			byte[] newBlock = new byte[Disk.blockSize];

			//erase block
			for(int i = 0; i < Disk.blockSize; i++){
				newBlock[i] = 0;
			}

			//set next block in new block to -1
			SysLib.int2bytes(-1,newBlock,0);

			//while the end of the free list has not been found, keep looking
			while(nextFree != -1){
				//get the next free block
				SysLib.rawread(nextFree, nextBlock);
				//check the byte id of the following free block
				temp = SysLib.bytes2int(nextBlock,0);

				//if the following free block is -1, this is the end of the queue
				if(temp == -1){
					//set next free block to the method argument and write to disk
					SysLib.int2bytes(blockNumber,nextBlock,0);
					SysLib.rawwrite(nextFree, nextBlock);
					SysLib.rawwrite(blockNumber,newBlock);
					//operation completed, return true
					return true;
				}

				//not at end yet, keep walking
				nextFree = temp;
			}			
		}

		//invalid block returned, do nothing and return false
		return false;
	}

	// Format Method
	// The public format method cleans the disk of all data and resets the correct structure if the
	// SuperBlock detects and illegal state during initialization of an instance. All instance variables
	// of SuperBlock are cleared to default values and written back to the newly cleared disk.
	public void format(int argInodeBlocks){
		//SysLib.cout("FORMATTING");

		// !!! WHO CLEANS  THE DISK, THIS OR FILESYSTEM
		//validate argument is positive int
		if(argInodeBlocks < 0){
			argInodeBlocks = defaultInodeBlocks;
		}	

		//set inodeBlocks property
		totalInodes = argInodeBlocks;

		//!!! FOR  TESTING ONLY
		inodeBlocks = totalInodes;

		//dummy inode for object creation
		Inode dummyInode = null;

		//create and write inodes to disk
		for(int i = 0; i < totalInodes; i++){
			dummyInode = new Inode();
			dummyInode.flag = 0;
			dummyInode.toDisk((short)i);
		}

		//SysLib.cout("INODES CREATED");

		//set free list head to first free block. first free block is after all iNodes are created
		//(16 per block) and space is allocated for totalBlocks, freeList, and totalInodes (index
		// 0 1 and 2)
		freeList = (totalInodes / 16) + 2;

		//create new dummy block for super
		byte[] newEmpty = null;

		//create default number of blocks and write to disk
		for(int i = freeList; i < defaultBlocks - 1; i++){
			newEmpty = new byte[Disk.blockSize];

			//erase block
			for(int j = 0; j < Disk.blockSize; j++){
				newEmpty[j] = 0;
			}

			//write the next sequential free block in pointer to next free
			SysLib.int2bytes(i+1, newEmpty, 0);

			//write block to disk
			SysLib.rawwrite(i, newEmpty);
		}

		//write final block
		newEmpty = new byte[Disk.blockSize];

		//erase block
		for(int j = 0; j < Disk.blockSize; j++){
			newEmpty[j] = 0;
		}

		//write the next sequential free block in pointer to next free
		SysLib.int2bytes(-1, newEmpty, 0);

		//write block to disk
		SysLib.rawwrite(defaultBlocks - 1, newEmpty);	

		//SysLib.cout("BLOCKS CREATED");

		//create and write new superblock to disk
		byte[] newSuper = new byte[Disk.blockSize];

		//write total number of blocks to new super
		SysLib.int2bytes(totalBlocks,newSuper,totalBlockLocation);
		//write total number of inodes to new super
		SysLib.int2bytes(totalInodes,newSuper,totalInodeLocation);
		//write free list to new super
		SysLib.int2bytes(freeList,newSuper,freeListLocation);

		//write new super to disk
		SysLib.rawwrite(0,newSuper);

		//SysLib.cout("NEW SUPER CREATED");
	}
}
