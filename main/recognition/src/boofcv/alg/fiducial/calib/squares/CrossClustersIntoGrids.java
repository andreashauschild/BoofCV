/*
 * Copyright (c) 2011-2015, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.fiducial.calib.squares;

import org.ddogleg.struct.FastQueue;

import java.util.ArrayList;
import java.util.List;

import static boofcv.misc.CircularIndex.addOffset;

/**
 * Takes as input a set of unordered cross connected clusters and converts them into ordered grids with known numbers
 * of rows and columns.  The output will be valid "chessboard" pattern. When rows and columns are discussed in the
 * code below it refers to both white and black squares in the chessboard. A row that starts with a white square
 * is referred to as white and one which starts with a black square as black.
 *
 * @author Peter Abeles
 */
public class CrossClustersIntoGrids {

	// verbose debug output
	private boolean verbose = false;

	FastQueue<SquareGrid> grids = new FastQueue<SquareGrid>(SquareGrid.class,true);

	/**
	 * Converts all the found clusters into grids, if they are valid.
	 *
	 * @param clusters List of clusters
	 */
	public void process( List<List<SquareNode>> clusters ) {
		for (int i = 0; i < clusters.size(); i++) {
			processCluster(clusters.get(i));
		}
	}

	/**
	 * Converts the cluster into a grid data structure.  If its not a grid then
	 * nothing happens
	 */
	protected void processCluster( List<SquareNode> cluster ) {
		// handle a special case
		if( cluster.size() == 1 ) {
			SquareNode n = cluster.get(0);
			if( n.getNumberOfConnections() == 0 ) {
				SquareGrid grid = grids.grow();
				grid.columns = grid.rows = 1;
				grid.nodes.clear();
				grid.nodes.add(n);
				return;
			}
		}

		for (int i = 0; i < cluster.size(); i++) {
			cluster.get(i).graph = SquareNode.RESET_GRAPH;
		}

		SquareNode seed = findSeedNode(cluster);

		if( seed == null )
			return;

		// find the first row
		List<SquareNode> firstRow;
		if( seed.getNumberOfConnections() == 1 ) {
			firstRow = firstRow1(seed);
		} else if( seed.getNumberOfConnections() == 2 ) {
			firstRow = firstRow2(seed);
		} else {
			throw new RuntimeException("BUG");
		}

		// Add the next rows to the list, one after another
		List<List<SquareNode>> listRows = new ArrayList<List<SquareNode>>();// TODO remove memory declaration here
		listRows.add(firstRow);
		while(true ) {
			List<SquareNode> previous = listRows.get(listRows.size()-1);
			if( !addNextRow(previous.get(0),listRows)) {
				break;
			}
		}

		// re-organize into a grid data structure
		SquareGrid grid = assembleGrid(firstRow, listRows);

		// check the grids connectivity
		if( !checkEdgeCount(grid) ) {
			grids.removeTail();
		}
	}

	private SquareGrid assembleGrid(List<SquareNode> firstRow, List<List<SquareNode>> listRows) {
		SquareGrid grid = grids.grow();

		// is the first column empty or not
		int offset = numberOfOpenEdges(firstRow.get(0)) == 1 ? 0 : 1;

		// If an end node has 2 edges that means there is another edge to its left or right
		grid.columns = firstRow.size() + offset;
		if( numberOfOpenEdges(firstRow.get(firstRow.size()-1)) != 1 )
			grid.columns++;
		grid.rows = listRows.size();

		// initialize grid to null
		grid.nodes.clear();
		for (int i = 0; i < grid.columns * grid.rows; i++) {
			grid.nodes.add(null);
		}

		// fill in the grid
		for (int row = 0; row < listRows.size(); row++) {
			List<SquareNode> list = listRows.get(row);
			int startCol = offset - row%2 != 0 ? 0 : 1;
			for (int col = startCol; col < grid.columns; col += 2) {
				grid.set(col,row,list.get(col/2));
			}
		}
		return grid;
	}

	/**
	 * Looks at the edge count in each node and sees if it has the expected number
	 */
	private boolean checkEdgeCount( SquareGrid grid ) {

		int left = 0, right = grid.columns-1;
		int top = 0, bottom = grid.rows-1;


		for (int row = 0; row < grid.rows; row++) {
			boolean skip = grid.get(row,0) == null;

			for (int col = 0; col < grid.columns; col++) {
				SquareNode n = grid.get(row,col);
				if( skip ) {
					if ( n != null )
						return false;
				} else {
					boolean horizontalEdge =  col == left || col == right;
					boolean verticalEdge =  row == top || row == bottom;

					boolean outer = horizontalEdge || verticalEdge;
					int connections = n.getNumberOfConnections();

					if( outer ) {
						if( horizontalEdge && verticalEdge ) {
							if( connections != 1 )
								return false;
						} else if( connections != 2 )
							return false;
					} else {
						if( connections != 4 )
							return false;
					}
				}
			}
		}
		return true;
	}

	private List<SquareNode> firstRow1( SquareNode seed ) {
		for (int i = 0; i < 4; i++) {
			if( isOpenEdge(seed,i) ) {
				List<SquareNode> list = new ArrayList<SquareNode>();
				list.add(seed);
				seed.graph = 0;
				addToRow(seed,i,1,true,list);
				return list;
			}
		}
		throw new RuntimeException("BUG");
	}

	private List<SquareNode> firstRow2(SquareNode seed ) {
		int indexLower = lowerEdgeIndex(seed);
		int indexUpper = addOffset(indexLower,1,4);

		List<SquareNode> listDown = new ArrayList<SquareNode>();
		List<SquareNode> list = new ArrayList<SquareNode>();

		addToRow(seed,indexLower,-1,true,listDown);
		flipAdd(listDown, list);
		list.add(seed);
		seed.graph = 0;
		addToRow(seed,indexUpper,1,true,list);

		return list;
	}

	/**
	 * Given a node, add all the squares in the row directly below it.  They will be ordered from "left" to "right".  The
	 * seed node can be anywhere in the row, e.g. middle, start, end.
	 *
	 * @return true if a row was added to grid and false if not
	 */
	boolean addNextRow( SquareNode seed , List<List<SquareNode>> grid ) {
		List<SquareNode> row = new ArrayList<SquareNode>();
		List<SquareNode> tmp = new ArrayList<SquareNode>();

		int numConnections = numberOfOpenEdges(seed);
		if( numConnections == 0 ) {
			return false;
		} else if( numConnections == 1 ) {
			for (int i = 0; i < 4; i++) {
				SquareEdge edge = seed.edges[i];
				if( edge != null ) {
					// see if the edge is one of the open ones
					SquareNode dst = edge.destination(seed);
					if( dst.graph != SquareNode.RESET_GRAPH)
						continue;

					// determine which direction to traverse along
					int corner = edge.destinationSide(seed);
					int l = addOffset(corner,-1,4);
					int u = addOffset(corner, 1,4);

					if( dst.edges[l] != null ) {
						addToRow(seed,i, 1,false,tmp);
						flipAdd(tmp, row);
					} else if( dst.edges[u] != null ){
						addToRow(seed,i, -1,false,row);
					} else {
						row.add(dst);
					}
					break;
				}
			}
		} else if( numConnections == 2 ) {
			int indexLower = lowerEdgeIndex(seed);
			int indexUpper = addOffset(indexLower,1,4);

			addToRow(seed,indexUpper, 1,false,tmp);
			flipAdd(tmp, row);
			addToRow(seed,indexLower,-1,false,row);
		} else {
			return false;
		}
		grid.add(row);
		return true;
	}

	private void flipAdd(List<SquareNode> tmp, List<SquareNode> row) {
		for (int i = tmp.size()-1;i>=0; i--) {
			row.add( tmp.get(i));
		}
	}

	/**
	 * Returns the index which comes first.  Assuming that there are two options
	 */
	static int lowerEdgeIndex( SquareNode node ) {
		if( isOpenEdge(node,0) ) {
			if( isOpenEdge(node,1)) {
				return 0;
			} else {
				return 3;
			}
		}

		// first find the index of the two corners and sanity check them
		for (int i = 1; i < 4; i++) {
			if( isOpenEdge(node,i) ) {
				return i;
			}
		}

		throw new RuntimeException("BUG!");
	}

	static int numberOfOpenEdges( SquareNode node ) {
		int total = 0;
		for (int i = 0; i < 4; i++) {
			if( isOpenEdge(node,i) )
				total++;
		}
		return total;
	}

	/**
	 * Is the edge open and can be traversed to?  Can't be null and can't have
	 * the marker set to a none RESET_GRAPH value.
	 */
	static boolean isOpenEdge( SquareNode node , int index ) {
		if( node.edges[index] == null )
			return false;
		int marker = node.edges[index].destination(node).graph;
		return marker == SquareNode.RESET_GRAPH;
	}

	/**
	 * Given a node and the corner to the next node down the line, add to the list every other node until
	 * it hits the end of the row.
	 * @param n Initial node
	 * @param corner Which corner points to the next node
	 * @param sign Determines the direction it will traverse.  -1 or 1
	 * @param skip true = start adding nodes at second, false = start first.
	 * @param row List that the nodes are placed into
	 */
	static void addToRow( SquareNode n , int corner , int sign , boolean skip ,
						   List<SquareNode> row ) {
		SquareEdge e;
		while( (e = n.edges[corner]) != null ) {
			if( e.a == n ) {
				n = e.b;
				corner = e.sideB;
			} else {
				n = e.a;
				corner = e.sideA;
			}
			if( !skip ) {
				if( n.graph != SquareNode.RESET_GRAPH)
					throw new RuntimeException("BUG!");
				n.graph = 0;
				row.add(n);
			}
			skip = !skip;
			sign *= -1;
			corner = addOffset(corner,sign,4);
		}
	}

	/**
	 * Finds a seed with 1 or 2 edges.
	 */
	static SquareNode findSeedNode(List<SquareNode> cluster) {
		SquareNode seed = null;

		for (int i = 0; i < cluster.size(); i++) {
			SquareNode n = cluster.get(i);
			int numConnections = n.getNumberOfConnections();
			if( numConnections == 0 || numConnections > 2 )
				continue;
			seed = n;
			break;
		}
		return seed;
	}

	public FastQueue<SquareGrid> getGrids() {
		return grids;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
}
