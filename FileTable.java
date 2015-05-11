/**
  FileTable Class
  Maintains the file structure table shared among user threads
  
  @author Diane Kerstein
  @author Magda Grzmiel
  @file FileTable.java
  @section 430 Final Project
  @date June 12, 2014
 */

import java.util.Vector;

public class FileTable {
    private Vector<FileTableEntry> table;   // the actual entity of file table
    private Directory dir;                  // the root directory
    public final static int UNUSED = 0;     // file does not exist
    public final static int USED = 1;       // file exists but is not R or W by anyone
    public final static int READ = 2;       // file is read by someone
    public final static int WRITE = 3;      // file is written by someone

    /**
    Constructor 
    
    Instantiates file structure table and sets dir to passed
    Directory reference
    @param directory a reference to a root Directory
    */
    public FileTable(Directory directory) { 
        // instantiate a file (structure) table
        table = new Vector<FileTableEntry>(); 
        // receive a reference to the Director from the file system
        dir = directory;           
    }

    /**
    falloc
    
    Allocates a new file table entry for passed filename, allocates and 
    retrieves register for the corresponding inode, increments the inode count,
    writes back inode to disk, and returns reference to this file table entry 
    
    @param filename a String representing the file name (for which a file 
    table entry will be created)
    @param mode a String representing the file access mode
    @return reference to allocated file table entry 
    */
    public synchronized FileTableEntry falloc(String filename, String mode) {
        short iNumber = -1; // inode number
        Inode inode = null; // holds inode 

        while (true) {
            // get the inumber form the inode for given file name
            iNumber = (filename.equals("/") ? (short) 0 : dir.namei(filename));

            // if the inode for the given file exist
            if (iNumber >= 0) {
                inode = new Inode(iNumber);

                // if the file is requesting ofr reading
                if (mode.equals("r")) {
                    
                    // and its flag is read or used or unused 
                    // (nobody has read or written to that file)
                    if (inode.flag == READ 
                        || inode.flag == USED 
                        || inode.flag == UNUSED) {

                        // change the flag of the node to read and break
                        inode.flag = READ;
                        break;
                    
                    // if the file is already written by someone, wait until finish
                    } else if (inode.flag == WRITE) {
                        try {
                            wait();
                        } catch (InterruptedException e) { }
                    }

                // if the file is requested for writing or writing/riding or append
                } else {
                    
                    // and the flag of that file is used, change the flag to write
                    if (inode.flag == USED || inode.flag == UNUSED) {
                        inode.flag = WRITE;
                        break;
                    
                    // if the flag is read or write, wait until they finish
                    } else {
                        try {
                            wait();
                        } catch (InterruptedException e) { }
                    }
                }

            // if the node for the given file does not exist,
            // create a new inode for that file, use the alloc function from
            // directory to get the inumber
            } else if (!mode.equals("r")) {
                iNumber = dir.ialloc(filename);
                inode = new Inode(iNumber);
                inode.flag = WRITE;
                break;

            } else {
                return null;
            }
        }

        inode.count++;  // increse the number of users
        inode.toDisk(iNumber);
        // create new file table entry and add it to the file table
        FileTableEntry entry = new FileTableEntry(inode, iNumber, mode);
        table.addElement(entry);
        return entry;
    }

    /**
    ffree

    Receives a file table entry references, saves the corresponding inode to
    the disk, frees its file table entry, and returns true if this file table 
    entry is found in the table 

    @param entry a FileTableEntry reference 
    @return boolean result of whether passed entry is found in table
    */
    public synchronized boolean ffree(FileTableEntry entry) {
        Inode inode = new Inode(entry.iNumber);
        // try to remove the given FileTableEntry, if it is in the table,
        // the remove methods will return true
        if (table.remove(entry)) {
            if (inode.flag == READ) {
                // if there is only one reader, set the flag to used (no more
                // users read that file) and wake up one thread(user)
                if (inode.count == 1) {
                    notify();
                    inode.flag = USED;
                }

            } else if (inode.flag == WRITE) {
                // set the flag to used
                inode.flag = USED;
                // wake up all threads (users) waiting for that file since
                // there might be threads waiting for a reading which can
                // execute in concurent mode.
                notifyAll();
            }

            // decrease the number of users of that file about one
            inode.count--;
            inode.toDisk(entry.iNumber);
            return true;
        }
        return false;
    }

    /**
    fempty

    Called before starting a format. Returns true if file table is empty
    
    @return boolean result if file table is empty or not
    */
    public synchronized boolean fempty() {
        return table.isEmpty();  // return if table is empty
    }                            
}
