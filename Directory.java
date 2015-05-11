// -----------------------------------------------------------------------------
// ---------------------------- Written by Josh Brunner ------------------------
// ------------------------- for CSS 430 HW5  Assignment -----------------------
// -------------------------- Last modified: 6/12/2014 -------------------------
// ------------------------------- Directory.java ------------------------------
/*
 * PURPOSE OF FILE
 * The "/" root directory maintains each file in a different directory entry
 * that contains its file name (in maximum 30 characters = in max. 60 bytes in
 * Java) and the corresponding inode number. The directory receives the maximum
 * number of inodes to be created, (i.e., thus the max. number of files to be
 * created) and keeps track of which inode numbers are in use.
 *
 * ASSUMPTIONS
 * 1. It is assumed that the user is running this file in ThreadOS's directory.
 * 2. File names are assumed to be unique. 
 */

import java.util.*;
import java.io.*;
import java.lang.*;

public class Directory {
    private final static int maxChars = 30;                 //max fnames chars 
    private final static int maxJava = 60;                  //max Java bytes
    private final static int BYTE_ALLOC = 64;               //maxJava + 4 short
    private final static int NEXT_CHUNK = 4;                //Used for offset
    private final static int ERROR = -1;                    //For clean reading
    private int dirSize;                                    //Directory size
    private int fsizes[];                                   //File sizes
    private char fnames[][];                                //File names

    private final static boolean SUCCESS = true;
    private final static boolean FAILURE = false;

    // -------------------------------------------------------------------------
    // Directory (constructor)
    /*
     * SUMMARY
     * This is the constructor for the Directory class. It recieves the desired
     * directory size, initalizes all file sizes to 0, creates the file name
     * array and places the "/" root directory within the first location.
     */  
    public Directory( int maxInumber ) {          
        fsizes = new int[maxInumber];                       //max stored files
        for ( int i = 0; i < maxInumber; i++ ){             //loop directory
            fsizes[i] = 0;                                  //init sizes to 0
        }
        dirSize = maxInumber;                               //save direct. size
        fnames = new char[maxInumber][maxChars];            //create file direct
        String root = "/";                                  //entry 0 is "/"
        fsizes[0] = root.length( );                         //save size of "/"
        root.getChars( 0, fsizes[0], fnames[0], 0 );        //place in fnames[0]
    }
    
    // -------------------------------------------------------------------------
    // bytes2directory
    /*
     * SUMMARY
     * This function is responsible for initializing the Directory instance 
     * with a byte array read from the disk. It accomplishes this by using
     * SysLib's bytes2int() function that captures the appropriate size of file.
     * After this, it loops over the file name array to read in the data content
     */       
    public void bytes2directory( byte[] data ) {
        int offset = 0;                                     //initialize offset
        for(int i = 0; i < dirSize; i++){                   //loop directory
            fsizes[i] = SysLib.bytes2int(data, offset);     //save file size
            offset += NEXT_CHUNK;                           //increment offset
        }
        for(int i = 0; i < dirSize; i++){                   //loop directory
            String tmpS = new String(data, offset, maxJava);//create a string ob
            tmpS.getChars(0, fsizes[i], fnames[i], 0);      //place in fnames[i]
            offset += maxJava;                              //increment offset
        }
    }           

    // -------------------------------------------------------------------------
    // directory2bytes
    /*
     * SUMMARY
     * This function is responsible for converting a Directory instance into a 
     * byte array that will be written to the disk. It accomplishes this by 
     * creating a byte array object of the appropriate size. It then loops over
     * the directory to capture and save the file sizes. Then, the actual data.
     * After this work is performed, it returns this byte array.
     */         
    public byte[] directory2bytes( ) {
        byte[] dirInfo = new byte[BYTE_ALLOC * dirSize];    //make new byte[]
        int offset = 0;                                     //initialize offset
        for(int i = 0; i < dirSize; i++){                   //loop directory
            SysLib.int2bytes(fsizes[i], dirInfo, offset);   //fill up directory
            offset += NEXT_CHUNK;                           //increment offset
        }
        for(int i = 0; i < dirSize; i++){                   //loop directory
            String tmpS = new String(fnames[i],0,fsizes[i]);//tmp string
            byte[] tmpByte = tmpS.getBytes();               //turn into byte[]
            System.arraycopy(tmpByte, 0, dirInfo, offset, tmpByte.length);
            offset += maxJava;                              //increment offset
        }
        return dirInfo;                                     //return new byte[]
    }
  
    // -------------------------------------------------------------------------
    // ialloc
    /*
     * SUMMARY
     * This function is responsible for allocating a new inode number for a 
     * specific filename passed in as a string. It uses a ternary operator to 
     * condense the code nessessary to get the smaller of two lengths. After
     * completion, the iNumber location is returned to the calling function.
     */      
    public short ialloc( String filename ) {
        for(short i = 0; i < dirSize; i++){                 //loop the directory
            if(fsizes[i] == 0){                             //find an empty file
                int fs = filename.length()>maxChars?maxChars:filename.length();
                fsizes[i] = fs;                             //save the file size
                filename.getChars(0,fsizes[i],fnames[i],0); //copy from string
                return i;                                   //return the iNumber
            }
        }
        return ERROR;                                       //No free spaces
    }
    
    // -------------------------------------------------------------------------
    // ifree
    /*
     * SUMMARY
     * When a valid iNumber is passed into this method, it goes to that iNumber 
     * location in the directory and marks its size to 0 -- meaning that it can 
     * be overwritten, if needed.
     */    
    public boolean ifree( short iNumber ) {
        if(iNumber < maxChars && fsizes[iNumber] > 0){      //If number is valid
            fsizes[iNumber] = 0;                            //Mark to be deleted
            return SUCCESS;                                 //File was found
        } else {                                     
            return FAILURE;                                 //File not found
        }
    }
    
    // -------------------------------------------------------------------------
    // namei
    /*
     * SUMMARY
     * Assuming all filenames are unique, this function loops over the directory
     * to try to find if a certain file is valid. If the size of the file being
     * searched for matches that of the size of some file in the directory, this
     * loop creates a new string to compare contents. In the event of a file 
     * being found successfully, the iNumber (location) is returned to the 
     * calling function. 
     */    
    public short namei( String filename ) {
     for(short i = 0; i < dirSize; i++){                    //loop directory
        if(filename.length() == fsizes[i]){                 //same string size
            String tmp = new String(fnames[i],0,fsizes[i]); //create a string 
            if(filename.equals(tmp)) return i;              //return iNumber 
        }
    }
    return ERROR;                                           //File not found
    }

    // -------------------------------------------------------------------------
    // printDirectory
    /*
     * SUMMARY
     * This is a neat private function that I use to print out the entire 
     * directory contents. It basically loops over the 2-d char array that 
     * holds the fnames and prints contents as it goes.
     */
    private void printDirectory(){
        for(int i = 0; i < dirSize; i++){                   //Loop downwards
            SysLib.cout(i+"[" + fsizes[i] + "]  ");         //Print the fsize
            for(int j = 0; j < maxChars; j++){              //Loop across
                SysLib.cout(fnames[i][j] + " ");            //Print out contents
            }
            SysLib.cout("\n");                              //New lines are cool
        }
    }    
}
