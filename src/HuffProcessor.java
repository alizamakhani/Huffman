import java.util.PriorityQueue;

/**
 *	Interface that all compression suites must implement. That is they must be
 *	able to compress a file and also reverse/decompress that process.
 * 
 *	@author Brian Lavallee
 *	@since 5 November 2015
 *  @author Owen Atrachan
 *  @since December 1, 2016
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;

	public enum Header{TREE_HEADER, COUNT_HEADER};
	public Header myHeader = Header.TREE_HEADER;
	
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
	    int[] counts = readForCounts(in);
	    HuffNode root = makeTreeFromCounts(counts);
	    String[] codings = new String[ALPH_SIZE+1];
	    makeCodingsFromTree(root, "", codings);
	   // for(int i=0; i<codings.length; i++)
	   // 		System.out.println(codings[i]);
	    
	    //in.reset();
	    writeHeader(root, out);
	    
	    in.reset();
	   // System.out.println(root.weight());
	    
	    writeCompressedBits(in, codings, out);
	}
	
	private int[] readForCounts(BitInputStream in) {
		int[] ret = new int[ALPH_SIZE];
		while(true) {
			int val = in.readBits(BITS_PER_WORD);
			if(val == -1) {
				//for(int i=0; i<ret.length; i++)
					//System.out.println(ret[i]);
				return ret;
			}
			ret[val] = ret[val] + 1;
		}
	}
	
	private HuffNode makeTreeFromCounts(int[] vals) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		//int count = 0;
		
		for(int i=0; i<vals.length; i++) {
			if(vals[i]>=1) {
				pq.add(new HuffNode(i, vals[i]));
			//	count++;
			}
		}
		
	//	System.out.println(count);
				
		pq.add(new HuffNode(ALPH_SIZE, 1));
				
		while (pq.size() > 1) {
		    HuffNode left = pq.poll();
		    HuffNode right = pq.poll();
		    //System.out.println(left.weight() + " " + right.weight());

		    HuffNode t = new HuffNode(-1, left.weight() + right.weight(), left,right);
		    pq.add(t);
		}
		
		HuffNode root = pq.poll();
		//System.out.println(root.weight());
		return root;

	}
	
	private void makeCodingsFromTree(HuffNode current, String path, String[] codings) {
		if(current.left() == null && current.right() == null) {
			codings[current.value()] = path;
//			System.out.println(current.value() + " " + path);
	//		System.out.println(current.weight());
			return;
		}
		
			makeCodingsFromTree(current.left(), path + "0", codings);
			makeCodingsFromTree(current.right(), path + "1", codings);
		
		
	}
	
	private void writeHeader (HuffNode root, BitOutputStream out) {
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		//System.out.println("HuffNum: " + HUFF_NUMBER + " Bits per int: " + BITS_PER_INT);
		//System.out.println("HuffTree: " + HUFF_TREE);
		writeTree(root, out);
	}
	
	private void writeTree(HuffNode root, BitOutputStream out) {
		// TODO Auto-generated method stub
		if(root.left()==null && root.right()==null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.value());
			//if(root.value() == PSEUDO_EOF)
				//System.out.println("found!");
			return;
		}
		else {
			out.writeBits(1, 0);
		}
				
		writeTree(root.left(), out);
		writeTree(root.right(), out);
	}

	private void writeCompressedBits(BitInputStream in, String[] codings, BitOutputStream out) {
		while(true) {
			int ch = in.readBits(BITS_PER_WORD);
			if(ch == -1)
				break;
			//System.out.println(codings.length);
			//System.out.println(codings[ch]);
			//for(int i=0; i<codings.length; i++)
				//System.out.println(codings[i]);
			String code = codings[ch];
			if(code!=null) {
				out.writeBits(code.length(), Integer.parseInt(code, 2));
			}
		}	
		
		String code = codings[PSEUDO_EOF];
		//System.out.println(code);
		//System.out.println(code.length());
		out.writeBits(code.length(), Integer.parseInt(code, 2));
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		in.reset();
	   int magic = in.readBits(BITS_PER_INT);
	  // System.out.println(magic);
	   //System.out.println(HUFF_NUMBER);
	   if(magic != HUFF_NUMBER && magic != HUFF_TREE)
		   throw new HuffException("No magic!");
		
	   HuffNode root = readTreeHeader(in); 
	   //System.out.println(root.weight() + " " + root.value());
	   readCompressedBits(root, in, out);
		
	}
	
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		// TODO Auto-generated method stub
		
		HuffNode current = root;
		while(true) {
			int bit = in.readBits(1);
			//System.out.println(bit);
			if(bit == -1)
				throw new HuffException("no PSEUDO_EOF??");
			if(bit == 1)
				current = current.right();
			if (bit == 0)
				current = current.left();
			
			if(current.left()==null && current.right()==null) {
				if(current.value() == PSEUDO_EOF) {
					break;
				}
				else {
					out.writeBits(BITS_PER_WORD, current.value());
					current = root;
				}
			}
				
		}
	}

	private HuffNode readTreeHeader(BitInputStream in) {
		//do a preorder traversal of the tree
		//return it
		
		if(in.readBits(1) == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(-1, 0, left, right);
		}
		else {
			return new HuffNode(in.readBits(BITS_PER_WORD+1),0);
		}
	}
	
	public void setHeader(Header header) {
        myHeader = header;
        System.out.println("header set to "+myHeader);
    }
}