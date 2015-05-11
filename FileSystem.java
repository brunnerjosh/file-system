/**
 FileSystem Class
 
 The file system class is responsible for performing all of the operations on 
 disk. It hides all of the implementation details from users by providing a list
 of operations which users can directly use. The class implements all the basic 
 functions of a file system as described in lecture, and makes appropriate calls
 to the components of our system to carry out fundamental actions like format, 
 open, write, read, delete, seek, and close. The file system can be viewed as 
 an API for other files or users to run commands against to access the file 
 system and its contents. The file system has the responsibility of 
 instantiating the other classes that compose our solution.
 
 @file FileSystem.java
 @author Diane Kerstein
 @author Greg White
 @author Josh Brunner
 @author Magda Grzmiel
 @author Jonathan Mason
 @section 430 Final Project
 @date June 12, 2014
 
 */

public class FileSystem {
    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;
    private final static boolean SUCCESS = true;
    private final static boolean FAILURE = false;

    public FileSystem(int diskBlocks) {
        // create superblock, and format disk with 64 inodes in default
        superblock = new SuperBlock(diskBlocks);

        // create directory, and register "/" in directory entry 0
        directory = new Directory(superblock.inodeBlocks);

        // file table is created, and store directory in the file table
        filetable = new FileTable(directory);

        // directory reconstruction
        FileTableEntry dirEnt = open("/", "r");
        int dirSize = fsize(dirEnt);
        if (dirSize > 0) {
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }
        close(dirEnt);
    }

    /**
    Sync Method
    
    The Sync Method syncs the file system back to the physical disk. The sync method will write
    the directory information to the disk in byte form in the root directory. The method will
    also ensure that the superblock is synced.
    */
    public void sync() {
        //open root directory with write access
        FileTableEntry openRoot = open("/", "w");

        //write directory to root
        write(openRoot, directory.directory2bytes());

        //close root directory
        close(openRoot);

        //sync superblock
        superblock.sync();
    }

    /**
    Format Method
   
    The format method performs a full format of the disk, erasing all the contents of the disk and
    regenerating the superblock, directory, and file tables. This operation is not reversable, once
    formatted all contents are lost. The argument stipulates the number of files (inodes) to be created
    by the superblock.
    */
    public boolean format(int files) {
        //call format on superblock for arg number of files
        superblock.format(files);

        // create directory, and register "/" in directory entry 0
        directory = new Directory(superblock.inodeBlocks);

        // file table is created, and store directory in the file table
        filetable = new FileTable(directory);

        //return true on completion
        return true;
    }

    /**
    open

    This function is responsible for opening a file specified by the filename
    String passed into it. In addition to the String object, it has passed 
    another String object to represent the mode that the filename object shall
    have once created. The function starts out by creating a new FileTableEntry
    object using filetable's falloc() function. Once that gets created, 
    it checks to see if the mode that was passed in is a "w" for write. If it 
    is, it deletes all blocks and starts writing from scratch. After this check 
    occurs, the new FileTableEntry object is returned to the calling function.
   */
    FileTableEntry open(String filename, String mode) {
        FileTableEntry newEntry = filetable.falloc(filename, mode);
        if (mode == "w") {                                //Is it writing mode?
            if (!deallocAllBlocks(newEntry))
                return null; //Delete all blocks first
        }
        return newEntry;                                //return new FT entry
    }

    /**
    Close Method
    
    This function closes the file corresponding to given file table entry.
    It returns true in the case of successful performing that operation,
    false otherwise.
    */
    public boolean close(FileTableEntry ftEnt) {
        synchronized (ftEnt) {
            // Decrese the number of users which use that file table entry
            ftEnt.count--;
            // If there are no more users using this file table entry,
            // free the file entry in the file table
            if (ftEnt.count == 0) {
                return filetable.ffree(ftEnt);

            }
            return true;
        }
    }

    /**
    fsize

    returns the size in bytes of the file indicated by ftEnt

    @param ftEnt a FileTableEntry to have its length returned
    @return size of passed FileTableEntry
   */
    int fsize(FileTableEntry ftEnt) {
        synchronized (ftEnt) {
            Inode tempInode = ftEnt.inode;
            return tempInode.length;
        }
    }

    /**
    read: 
    
    Read operation runs atomically. Checks target block to make sure it is
    valid to read from. Else break. Then reads block and calulates the
    buffer based on data size. The amount of data read during each loop is
    determined by the buffer size, and it gets read from the ftEnt.
    */
    int read(FileTableEntry ftEnt, byte[] buffer) {
        if ((ftEnt.mode == "w") || (ftEnt.mode == "a"))
            return -1;

        int size = buffer.length;               //total size of data to read
        int readBuffer = 0;                     //used to track data read
        int readError = -1;                     //checks for error on read
        int blockSize = 512;
        int iterationSize = 0;                  //tracks how much is left to read

        synchronized (ftEnt) {
            // loop to read chunks of data
            while ((ftEnt.seekPtr < fsize(ftEnt) && (size > 0))) {
                // prep to read data from block if valid
                int target = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                if (target == readError) {
                    break;
                }
                // read block of data
                byte[] data = new byte[blockSize];
                SysLib.rawread(target, data);

                // set pointer to read block data
                int dataOffset = ftEnt.seekPtr % blockSize;
                // get what is left in block
                int blockLeft = blockSize - dataOffset;
                // check how much file left
                int fileLeft = fsize(ftEnt) - ftEnt.seekPtr;

                if (blockLeft < fileLeft)
                    iterationSize = blockLeft;
                else
                    iterationSize = fileLeft;

                if (iterationSize > size)
                    iterationSize = size;

                //copy over data read to buffer
                System.arraycopy(data, dataOffset, buffer, readBuffer,
                        iterationSize);

                //update variables for next iteration
                ftEnt.seekPtr += iterationSize;
                readBuffer += iterationSize;
                size -= iterationSize;
            }
            return readBuffer;
        }
    }

    /**
    write
   
    Writes the contents of buffer to the file indicated by ftEnt, starting at the position 
    indicated by the seek pointer. Increments the seek pointer by the number of bytes to 
    have been written. The return value is the number of bytes that have been written, or a 
    negative value upon an error.

    @param ftEnt a FileTableEntry to be written to
    @param buffer a byte array that will be written to ftEnt
    @return int value of number of bytes that have been written. -1 if error
   */
    int write(FileTableEntry ftEnt, byte[] buffer) {
        int bytesWritten = 0; // bytes that have been written
        int bufferSize = buffer.length; // remaining size of buffer
        int blockSize = 512;

        // error checking
        if (ftEnt == null || ftEnt.mode == "r") {
            return -1;
        }

        synchronized (ftEnt) {
            while (bufferSize > 0) {
                // location of block to read from
                int loc = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);

                // if current block is null
                if (loc == -1) {
                    // find new free block to write to
                    short newLoc = (short) superblock.getFreeBlock();

                    //gets index block number and test pointers
                    int testPtr = ftEnt.inode.getIndexBlockNumber(ftEnt.seekPtr, newLoc);
                    
                    //Error on write to NULL pointer
                    if(testPtr == -3){
                        short freeBlock = (short)this.superblock.getFreeBlock();
                        
                        //Indirect pointer is != -1
                        if (!ftEnt.inode.setIndexBlock(freeBlock)) {
                            return -1;
                        }

                        //Has error on block pointer
                        if (ftEnt.inode.getIndexBlockNumber(ftEnt.seekPtr, newLoc) != 0) {
                            return -1;
                        }                        
                    //Error on write to used or unused block
                    } else if (testPtr == -2 || testPtr == -1){
                        return -1;
                    }
                    loc = newLoc;
                }
                
                byte[] tempBuffer = new byte[blockSize];    // create new byte array
                SysLib.rawread(loc, tempBuffer);            // read block into memory

                int tempPtr = ftEnt.seekPtr % blockSize;    // walks through file
                int diff = blockSize - tempPtr;             // size difference between blocks

                // append to end if diff is bigger than size of buffer
                if (diff > bufferSize) {
                  System.arraycopy(buffer, bytesWritten, tempBuffer, tempPtr, bufferSize);
                  SysLib.rawwrite(loc, tempBuffer);         // write block to memory

                  ftEnt.seekPtr += bufferSize;              // increment seekptr
                  bytesWritten += bufferSize;               // increment bytes written
                  bufferSize = 0;                           // update buff size

                // copy remaining block to array
                } else {                                   
                   System.arraycopy(buffer, bytesWritten, tempBuffer, tempPtr, diff);
                   SysLib.rawwrite(loc, tempBuffer);        // write block to memory

                   ftEnt.seekPtr += diff;                   // increment seekptr
                   bytesWritten += diff;                    // increment bytes written
                   bufferSize -= diff;                      // decrement remaining buff size
                }

            }
            // update inode length if seekPtr is bigger
            if (ftEnt.seekPtr > ftEnt.inode.length) {
                ftEnt.inode.length = ftEnt.seekPtr;
            }

            ftEnt.inode.toDisk(ftEnt.iNumber);              // save inode to Disk
            return bytesWritten;
        }
    }

    /**
    deallocAllBlocks: 
    
    Checks if inodes blocks are valid, else error. Then runs through all
    the direct pointer blocks and calls superblock to return if valid.
    Then handles indirect ptr from indode and calls returnBlock().
    Finally writes back inodes to disk. 
    */
    private boolean deallocAllBlocks(FileTableEntry ftEnt) {
        short notValid = -1; //can't read
        // return if inode is null
        if (ftEnt.inode.count != 1) {
            SysLib.cerr("Null pointer - could not deallocAllBlocks.\n");
            return false;
        }

        //handle direct pointer blocks
        for (short blockId = 0; blockId < ftEnt.inode.directSize; blockId++) {
            if (ftEnt.inode.direct[blockId] != notValid) {
                superblock.returnBlock(blockId);
                ftEnt.inode.direct[blockId] = notValid;
            }
        }

        //get any data from indirect ptr
        byte[] data = ftEnt.inode.freeIndirectBlock();
        //handle block from direct pointer if != null
        if (data != null) {
            short blockId;
            while ((blockId = SysLib.bytes2short(data, 0)) != notValid) {
                superblock.returnBlock(blockId);
            }
        }
        ftEnt.inode.toDisk(ftEnt.iNumber);//write back inodes to disk
        return true;
    }

    /**
    delete

    This function is responsible for deleting a specified file as per 
    determined by the filename string param passed in. It begins by opening
    and creating a temporary FileTableEntry object to contain the iNode (TCB)
    object. This allows us to have access to all private members of this
    desired filename entry. With this iNode, we use it's iNumber to free it
    up from Directory's tables. Afterwards, we close the FileTableEntry 
    object using the close() function. As long as both the ifree() and
    close() are successful, we return true. Otherwise we return false
    indicating that it is still open elsewhere.
   */
    boolean delete(String filename) {
        FileTableEntry tcb = open(filename, "w");       //Grab the TCB (iNode)
        if (directory.ifree(tcb.iNumber) && close(tcb)) { //try to free and
            // delete
            return SUCCESS;                              //Delete was completed
        } else {
            return FAILURE;                              //Was not last open
        }
    }

    // Start position of the updating file pointer
    private final int SEEK_SET = 0; // from the beginning of the file
    private final int SEEK_CUR = 1; // from the current position of the file pointer in the file
    private final int SEEK_END = 2; // from the end of the file

    /**
    Seek Method
    
    This function updates the seek pointer corresponding to given file table entry.
    Returns 0 if the update was succesfull, -1 otherwise.
    */
    int seek(FileTableEntry ftEnt, int offset, int whence) {
        synchronized (ftEnt) {
            switch (whence) {
                // If from the beginning of the file,
                case SEEK_SET:
                    // Sets the file's seek pointer to the offset bytes from
                    // the beginning of the file
                    ftEnt.seekPtr = offset;
                    break;

                // If from the current position of the file pointer,
                case SEEK_CUR:
                    // Set the file's seek pointer to its current value plus
                    // the offset
                    ftEnt.seekPtr += offset;
                    break;

                // If from the end of the file,
                case SEEK_END:
                    // Set the file's seek pointer to the size of the file plus
                    // the offset
                    // get the size of the file by using the length form inode
                    ftEnt.seekPtr = ftEnt.inode.length + offset;
                    break;

                // Return unsuccess (-1)
                default:
                    return -1;
            }

            // In the case that the user attempts to set the seek pointer to
            // a negative number, set it to zero.
            if (ftEnt.seekPtr < 0) {
                ftEnt.seekPtr = 0;
            }

            // In the case that the user attempts to set the pointer to
            // beyond the file size, sets the seek pointer to the end of the
            // file.
            if (ftEnt.seekPtr > ftEnt.inode.length) {
                ftEnt.seekPtr = ftEnt.inode.length;
            }

            // Return success (0)
            return ftEnt.seekPtr;
        }
    }
}

