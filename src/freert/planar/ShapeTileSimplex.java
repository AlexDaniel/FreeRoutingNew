/*
 *  Copyright (C) 2014  Alfons Wirtz  
 *   website www.freerouting.net
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License at <http://www.gnu.org/licenses/> 
 *   for more details.
 */

package freert.planar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Convex shape defined as intersection of half-planes. 
 * A half-plane is defined as the positive side of a directed line.
 * This is actually used for the search tree, so, rational points are just squashed here....
 * They are not used for parts, pin locations or other stuff, just traces....
 * Note that it is quite possible to have one line as simplex or two lines, meaning that the simplex may not be closed
 *
 * @author Alfons Wirtz
 */

public final class ShapeTileSimplex extends ShapeTile
   {
   private static final long serialVersionUID = 1L;
   // Standard implementation for an empty Simplex.
   public static final ShapeTileSimplex EMPTY = new ShapeTileSimplex(new ArrayList<PlaLineInt>());
   
   private final ArrayList<PlaLineInt> lines_list;

   // the following fields are for storing pre calculated data
   transient private PlaPointInt[]    precalc_corners_int = null;
   transient private PlaPointFloat[]  precalc_corners_float = null;
   transient private ShapeTileBox     precalc_bounding_box = null;
   transient private ShapeTileOctagon precalc_bounding_octagon = null;
   

   public ShapeTileSimplex ( PlaLineInt p_line )
      {
      lines_list = new ArrayList<PlaLineInt>(1);
      
      lines_list.add(p_line);
      }

   /**
    * Constructs a Simplex from the directed lines in p_line_arr. 
    * The simplex will not be normalized. To get a normalized simplex use TileShape.get_instance
    * Careful, the arraylist is not copyed
    * @param p_line_arr
    */
   public ShapeTileSimplex(ArrayList<PlaLineInt> p_line_arr)
      {
      lines_list = p_line_arr;

      Collections.sort(lines_list);
      }
   
   /**
    * To be used when you know that the lines are surely OK
    * Example, when creating a new object from a mirros or rotate operation
    * @param p_line_alist
    */
   public ShapeTileSimplex(PlaLineIntAlist p_line_alist)
      {
      lines_list = new ArrayList<PlaLineInt>(p_line_alist.size());
      
      for ( PlaLineInt a_line : p_line_alist ) lines_list.add(a_line);

      Collections.sort(lines_list);
      }


   /**
    * creates a Simplex as intersection of the halfplanes defined by an array of directed lines
    */
   public static ShapeTileSimplex get_instance(PlaLineIntAlist p_line_alist)
      {
      if (p_line_alist.size() <= 0) return ShapeTileSimplex.EMPTY;
      
      ShapeTileSimplex curr_simplex = new ShapeTileSimplex(p_line_alist);
      
      return curr_simplex.remove_redundant_lines();
      }
   
   
   
   /**
    * @return true, if this simplex is empty
    */
   @Override
   public boolean is_empty()
      {
      return lines_size() == 0;
      }

   private int lines_size()
      {
      return lines_list.size();
      }
   
   /**
    * Returns the number of edge lines defining this simplex
    */
   @Override
   public int border_line_count()
      {
      return lines_size();
      }
   
   
   public PlaLineInt tline_get (int index)
      {
      return lines_list.get(index);
      }
   
   /**
    * Converts the physical instance of this shape to a simpler physical instance, if possible. 
    * (For example a Simplex to an IntOctagon).
    */
   @Override
   public ShapeTile simplify()
      {
      if ( is_empty())
         return ShapeTileSimplex.EMPTY;
      else if (is_IntBox())
         return bounding_box();
      else if (is_IntOctagon())
         return to_octagon();
      else
         return this;
      }

   /**
    * Quite often there is the need to get the index of the "previous" line
    * @param cur_index
    * @return
    */
   private int get_prev_index ( int cur_index )
      {
      return cur_index == 0 ? lines_size() - 1 : cur_index - 1;
      }

   
   /**
    * @returns true, if the determinant of the direction of index p_no -1 and the direction of index p_no is > 0
    */
   @Override
   public boolean corner_is_bounded(int p_no)
      {
      if (lines_size() == 1) return false;

      if (p_no < 0)
         {
         System.out.println("corner: p_no is < 0");
         p_no = 0;
         }
      else if (p_no >= lines_size())
         {
         System.out.println("corner: p_index must be less than arr.length - 1");
         p_no = lines_size() - 1;
         }

      
      int prev_no = get_prev_index(p_no);
      
      PlaDirection prev_dir = tline_get(prev_no).direction();
      PlaDirection curr_dir = tline_get(p_no).direction();
      
      return prev_dir.determinant(curr_dir) > 0;
      }

   /**
    * @return true, if the shape of this simplex is contained in a sufficiently large box
    */
   @Override
   public boolean is_bounded()
      {
      if (lines_size() == 0)  return true;

      if (lines_size() < 3)  return false;
 
      for (int index = 0; index < lines_size(); ++index)
         {
         if (! corner_is_bounded(index)) return false;
         }

      return true;
      }


   @Override
   public PlaPointInt corner(int p_no)
      {
      if (p_no < 0)
         {
         System.out.println("Simplex.corner: p_no is < 0");
         p_no = 0;
         }
      else if (p_no >= lines_size())
         {
         System.out.println("Simplex.corner: p_no must be less than arr.length - 1");
         p_no = lines_size() - 1;
         }

      if (precalc_corners_int == null)  precalc_corners_int = new PlaPointInt[lines_size()];
      
      if (precalc_corners_int[p_no] != null) return precalc_corners_int[p_no];

      // corner is not yet calculated
      PlaLineInt prev = tline_get(get_prev_index(p_no));
      
      precalc_corners_int[p_no] = tline_get(p_no).intersection(prev,"should not heppen").round();

      return precalc_corners_int[p_no];
      }

   /**
    * Returns an approximation of the intersection of the p_no -1-th with the p_no-th line of this simplex by a FloatPoint. If the
    * simplex is not bounded at this corner, the coordinates of the result will be set to Integer.MAX_VALUE.
    */
   @Override
   public PlaPointFloat corner_approx(int p_no)
      {
      if (lines_size() == 0) return null;

      if (p_no < 0)
         {
         System.out.println("Simplex.corner_approx: p_no is < 0");
         p_no = 0;
         }
      else if (p_no >= lines_size())
         {
         System.out.println("Simplex.corner_approx: p_no must be less than arr.length - 1");
         p_no = lines_size() - 1;
         }
      
      if (precalc_corners_float == null) precalc_corners_float = new PlaPointFloat[lines_size()];
      
      if (precalc_corners_float[p_no] != null) return precalc_corners_float[p_no];

      // corner is not yet calculated
      PlaLineInt prev = tline_get(get_prev_index(p_no));
      
      precalc_corners_float[p_no] = tline_get(p_no).intersection_approx(prev);

      return precalc_corners_float[p_no];
      }

   @Override   
   public PlaPointFloat[] corner_approx_arr()
      {
      if (precalc_corners_float == null) precalc_corners_float = new PlaPointFloat[lines_size()];

      for (int index = 0; index < precalc_corners_float.length; ++index)
         {
         if (precalc_corners_float[index] != null) continue;

         PlaLineInt prev = tline_get(get_prev_index(index));

         precalc_corners_float[index] = tline_get(index).intersection_approx(prev);
         }

      return precalc_corners_float;
      }

   /**
    * @returns the p_no-th edge line of this simplex. 
    * The edge lines are sorted in ascending direction.
    */
   @Override   
   public PlaLineInt border_line(int p_no)
      {
      if (lines_size() <= 0)
         {
         System.out.println("Simplex.edge_line : simplex is empty");
         return null;
         }
      
      if (p_no < 0)
         {
         System.out.println("Simplex.edge_line : p_no is < 0");
         p_no = 0;
         }
      else if (p_no >= lines_size())
         {
         System.out.println("Simplex.edge_line: p_no must be less than arr.length - 1");
         p_no = lines_size() - 1;
         }

      return tline_get(p_no);
      }

   /**
    * Returns the dimension of this simplex. The result may be 2, 1, 0, or -1 (if the simplex is empty).
    */
   @Override   
   public PlaDimension dimension()
      {
      if (lines_size() == 0) return PlaDimension.EMPTY;

      if (lines_size() > 4) return PlaDimension.AREA;

      // we have a half plane
      if (lines_size() == 1) return PlaDimension.AREA;
      
      if (lines_size() == 2)
         {
         if (tline_get(0).overlaps(tline_get(1)))
            {
            return PlaDimension.LINE;
            }
         return PlaDimension.AREA;
         }
      
      if (lines_size() == 3)
         {
         if (tline_get(0).overlaps(tline_get(1)) || tline_get(0).overlaps(tline_get(2)) || tline_get(1).overlaps(tline_get(2)))
            {
            // simplex is 1 dimensional and unbounded at one side
            return PlaDimension.LINE;
            }

         PlaPoint intersection = tline_get(1).intersection(tline_get(2), "what does this do ?");
         
         PlaSide side_of_line0 = tline_get(0).side_of(intersection);
         if (side_of_line0 == PlaSide.ON_THE_RIGHT)
            {
            return PlaDimension.AREA;
            }
         
         if (side_of_line0 == PlaSide.ON_THE_LEFT)
            {
            System.out.println("empty Simplex not normalized");
            return PlaDimension.EMPTY;
            }
         
         // now the 3 lines intersect in the same point
         return PlaDimension.POINT;
         }
      
      // now the simplex has 4 edge lines check if opposing lines are collinear
      boolean collinear_0_2 = tline_get(0).overlaps(tline_get(2));
      boolean collinear_1_3 = tline_get(1).overlaps(tline_get(3));
      if (collinear_0_2 && collinear_1_3)
         {
         return PlaDimension.POINT;
         }
      if (collinear_0_2 || collinear_1_3)
         {
         return PlaDimension.LINE;
         }
      return PlaDimension.AREA;
      }

   @Override   
   public double max_width()
      {
      if (!is_bounded())
         {
         return Integer.MAX_VALUE;
         }
      
      double max_distance = Integer.MIN_VALUE;
      double max_distance_2 = Integer.MIN_VALUE;
      
      PlaPointFloat gravity_point = centre_of_gravity();

      for (int index = 0; index < border_line_count(); ++index)
         {
         double curr_distance = Math.abs(tline_get(index).distance_signed(gravity_point));

         if (curr_distance > max_distance)
            {
            max_distance_2 = max_distance;
            max_distance = curr_distance;
            }
         else if (curr_distance > max_distance_2)
            {
            max_distance_2 = curr_distance;
            }
         }
      return max_distance + max_distance_2;
      }

   @Override   
   public double min_width()
      {
      if (!is_bounded()) return Integer.MAX_VALUE;

      double min_distance = Integer.MAX_VALUE;
      double min_distance_2 = Integer.MAX_VALUE;
      PlaPointFloat gravity_point = centre_of_gravity();

      for (int index = 0; index < border_line_count(); ++index)
         {
         double curr_distance = Math.abs(tline_get(index).distance_signed(gravity_point));

         if (curr_distance < min_distance)
            {
            min_distance_2 = min_distance;
            min_distance = curr_distance;
            }
         else if (curr_distance < min_distance_2)
            {
            min_distance_2 = curr_distance;
            }
         }
      return min_distance + min_distance_2;
      }

   /**
    * checks if this simplex can be converted into an IntBox
    */
   @Override   
   public boolean is_IntBox()
      {
      for (int index = 0; index < lines_size(); ++index)
         {
         PlaLineInt curr_line = tline_get(index);

         if ( ! curr_line.is_orthogonal()) return false;

         if ( ! corner_is_bounded(index)) return false;
         }

      return true;
      }

   /**
    * checks if this simplex can be converted into an IntOctagon
    */
   @Override   
   public boolean is_IntOctagon()
      {
      for (int index = 0; index < lines_size(); ++index)
         {
         PlaLineInt curr_line = tline_get(index);

         if ( ! curr_line.is_multiple_of_45_degree()) return false;

         if ( ! corner_is_bounded(index)) return false;
         }

      return true;
      }

   /**
    * Converts this IntSimplex to an IntOctagon. 
    * @returns null, if that is not possible, because not all lines of this IntSimplex are 45 degree
    */
   public ShapeTileOctagon to_octagon()
      {
      if ( ! is_IntOctagon()) return null;

      if (is_empty()) return ShapeTileOctagon.EMPTY;

      // initialise to biggest octagon values

      int rx = PlaLimits.CRIT_INT;
      int uy = PlaLimits.CRIT_INT;
      int lrx = PlaLimits.CRIT_INT;
      int urx = PlaLimits.CRIT_INT;
      int lx = -PlaLimits.CRIT_INT;
      int ly = -PlaLimits.CRIT_INT;
      int llx = -PlaLimits.CRIT_INT;
      int ulx = -PlaLimits.CRIT_INT;

      for (int index = 0; index < lines_size(); ++index)
         {
         PlaLineInt curr_line = tline_get(index);
         PlaPointInt a = curr_line.point_a;
         PlaPointInt b = curr_line.point_b;
         if (a.v_y == b.v_y)
            {
            if (b.v_x >= a.v_x)
               {
               // lower boundary line
               ly = a.v_y;
               }
            if (b.v_x <= a.v_x)
               {
               // upper boundary line
               uy = a.v_y;
               }
            }
         if (a.v_x == b.v_x)
            {
            if (b.v_y >= a.v_y)
               {
               // right boundary line
               rx = a.v_x;
               }
            if (b.v_y <= a.v_y)
               {
               // left boundary line
               lx = a.v_x;
               }
            }
         if (a.v_y < b.v_y)
            {
            if (a.v_x < b.v_x)
               {
               // lower right boundary line
               lrx = a.v_x - a.v_y;
               }
            else if (a.v_x > b.v_x)
               {
               // upper right boundary line
               urx = a.v_x + a.v_y;
               }
            }
         else if (a.v_y > b.v_y)
            {
            if (a.v_x < b.v_x)
               {
               // lower left boundary line
               llx = a.v_x + a.v_y;
               }
            else if (a.v_x > b.v_x)
               {
               // upper left boundary line
               ulx = a.v_x - a.v_y;
               }
            }
         }
      ShapeTileOctagon result = new ShapeTileOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
      return result.normalize();
      }

   /**
    * Returns the simplex, which results from translating the lines of this simplex by p_vector
    */
   @Override   
   public ShapeTileSimplex translate_by(PlaVectorInt p_vector)
      {
      if (p_vector.equals(PlaVectorInt.ZERO)) return this;

      ArrayList<PlaLineInt> new_arr = new ArrayList<PlaLineInt>(lines_size());
      
      for (int index = 0; index < lines_size(); ++index)
         new_arr.add( tline_get(index).translate_by(p_vector) );
      
      return new ShapeTileSimplex(new_arr);
      }

   /**
    * @returns the smallest box with int coordinates containing all corners of this simplex. 
    * The coordinates of the result will be Integer.MAX_VALUE, if the simplex is not bounded
    */
   @Override
   public ShapeTileBox bounding_box()
      {
      if (lines_size() == 0) return ShapeTileBox.EMPTY;

      if (precalc_bounding_box != null) return precalc_bounding_box;

      double llx = Integer.MAX_VALUE;
      double lly = Integer.MAX_VALUE;
      double urx = Integer.MIN_VALUE;
      double ury = Integer.MIN_VALUE;

      for (int index = 0; index < lines_size(); ++index)
         {
         PlaPointFloat curr = corner_approx(index);
         llx = Math.min(llx, curr.v_x);
         lly = Math.min(lly, curr.v_y);
         urx = Math.max(urx, curr.v_x);
         ury = Math.max(ury, curr.v_y);
         }
      
      PlaPointInt lower_left = new PlaPointInt(Math.floor(llx), Math.floor(lly));
      PlaPointInt upper_right = new PlaPointInt(Math.ceil(urx), Math.ceil(ury));
      
      precalc_bounding_box = new ShapeTileBox(lower_left, upper_right);

      return precalc_bounding_box;
      }

   /**
    * Calculates a bounding octagon of the Simplex. 
    * @returns null, if the Simplex is not bounded.
    */
   @Override   
   public ShapeTileOctagon bounding_octagon()
      {
      if (precalc_bounding_octagon != null) return precalc_bounding_octagon;
      
      double lx = Integer.MAX_VALUE;
      double ly = Integer.MAX_VALUE;
      double rx = Integer.MIN_VALUE;
      double uy = Integer.MIN_VALUE;
      double ulx = Integer.MAX_VALUE;
      double lrx = Integer.MIN_VALUE;
      double llx = Integer.MAX_VALUE;
      double urx = Integer.MIN_VALUE;

      for (int index = 0; index < lines_size(); ++index)
         {
         PlaPointFloat curr = corner_approx(index);
         lx = Math.min(lx, curr.v_x);
         ly = Math.min(ly, curr.v_y);
         rx = Math.max(rx, curr.v_x);
         uy = Math.max(uy, curr.v_y);

         double tmp = curr.v_x - curr.v_y;
         ulx = Math.min(ulx, tmp);
         lrx = Math.max(lrx, tmp);

         tmp = curr.v_x + curr.v_y;
         llx = Math.min(llx, tmp);
         urx = Math.max(urx, tmp);
         }
      
      precalc_bounding_octagon = new ShapeTileOctagon(
            Math.floor(lx), 
            Math.floor(ly), 
            Math.ceil(rx),
            Math.ceil(uy),
            Math.floor(ulx),
            Math.ceil(lrx),
            Math.floor(llx),
            Math.ceil(urx));

      return precalc_bounding_octagon;
      }

   @Override   
   public ShapeTileSimplex bounding_tile()
      {
      return this;
      }

   @Override   
   public ShapeTileRegular bounding_shape()
      {
      return bounding_octagon();
      }

   /**
    * Returns the simplex offseted by p_with. If p_width > 0, the offset is to the outer, else to the inner.
    */
   @Override   
   public ShapeTileSimplex offset(double p_width)
      {
      if (p_width == 0) return this;

      ArrayList<PlaLineInt> new_arr = new ArrayList<PlaLineInt>(lines_size());
      for (int index = 0; index < lines_size(); ++index)
         {
         new_arr.add(tline_get(index).translate(-p_width));
         }
      
      ShapeTileSimplex offset_simplex = new ShapeTileSimplex(new_arr);
      
      if (p_width < 0) offset_simplex = offset_simplex.remove_redundant_lines();
      
      return offset_simplex;
      }

   /**
    * Returns this simplex enlarged by p_offset. 
    * The result simplex is intersected with the by p_offset enlarged bounding octagon of this simplex
    */
   @Override   
   public ShapeTileSimplex enlarge(double p_offset)
      {
      if (p_offset == 0) return this;

      ShapeTileSimplex offset_simplex = offset(p_offset);
      
      ShapeTileOctagon bounding_oct = bounding_octagon();

      if (bounding_oct.is_NaN() ) return ShapeTileSimplex.EMPTY;
      
      ShapeTileOctagon offset_oct = bounding_oct.offset(p_offset);
      
      return offset_simplex.intersection(offset_oct.to_Simplex());
      }


   /**
    * Returns the intersection of p_box with this simplex
    */
   @Override   
   public ShapeTileSimplex intersection(ShapeTileBox p_box)
      {
      return intersection(p_box.to_Simplex());
      }

   /**
    * Returns the intersection of this simplex and p_other
    */
   @Override   
   public ShapeTileSimplex intersection(ShapeTileSimplex p_other)
      {
      if (is_empty() || p_other.is_empty()) return EMPTY;
      
      ArrayList<PlaLineInt>  new_arr = new ArrayList<PlaLineInt>(lines_size() + p_other.lines_size());
      new_arr.addAll(lines_list);
      new_arr.addAll(p_other.lines_list);
      
      ShapeTileSimplex result = new ShapeTileSimplex(new_arr);
      
      return result.remove_redundant_lines();
      }

   /**
    * Returns the intersection of this simplex and the shape p_other
    */
   @Override   
   public ShapeTile intersection(ShapeTile p_other)
      {
      return p_other.intersection(this);
      }

   @Override   
   public boolean intersects(PlaShape p_other)
      {
      return p_other.intersects(this);
      }

   @Override   
   public boolean intersects(ShapeTileSimplex p_other)
      {
      ShapeConvex is = intersection(p_other);
      
      return ! is.is_empty();
      }

   /**
    * if p_line is a borderline of this simplex the number of that edge is returned, otherwise -1
    */
   @Override   
   public int border_line_index(PlaLineInt p_line)
      {
      for (int index = 0; index < lines_size(); ++index)
         {
         if (p_line.equals(tline_get(index)))
            {
            return index;
            }
         }
      return -1;
      }

   /**
    * Enlarges the simplex by removing the edge line with index p_no. The result simplex may be unbounded.
    */
   public ShapeTileSimplex remove_border_line(int p_no)
      {
      if (p_no < 0 || p_no >= lines_size())
         {
         return this;
         }
      
      ArrayList<PlaLineInt> new_arr = new ArrayList<PlaLineInt>(lines_size());
      new_arr.addAll(lines_list);
      
      new_arr.remove(p_no);
      
      return new ShapeTileSimplex(new_arr);
      }


   @Override   
   public ShapeTileSimplex to_Simplex()
      {
      return this;
      }

   @Override   
   ShapeTileSimplex intersection(ShapeTileOctagon p_other)
      {
      return intersection(p_other.to_Simplex());
      }

   @Override   
   public ShapeTile[] cutout(ShapeTile p_shape)
      {
      return p_shape.cutout_from(this);
      }

   /**
    * cuts this simplex out of p_outer_simplex. 
    * Divides the resulting shape into simplices along the minimal distance lines from the
    * vertices of the inner simplex to the outer simplex; Returns the convex pieces constructed by this division.
    */
   @Override   
   public ShapeTileSimplex[] cutout_from(ShapeTileSimplex p_outer_simplex)
      {
      if ( ! dimension().is_area() )
         {
         System.out.println("Simplex.cutout_from only implemented for 2-dim simplex");
         return null;
         }
      
      ShapeTileSimplex inner_simplex = intersection(p_outer_simplex);
      
      if ( ! inner_simplex.dimension().is_area() )
         {
         // nothing to cutout from p_outer_simplex
         ShapeTileSimplex[] result = new ShapeTileSimplex[1];
         result[0] = p_outer_simplex;
         return result;
         }
      
      int inner_corner_count = inner_simplex.lines_size();
      PlaLineInt[][] division_line_arr = new PlaLineInt[inner_corner_count][];
      for (int inner_corner_no = 0; inner_corner_no < inner_corner_count; ++inner_corner_no)
         {
         division_line_arr[inner_corner_no] = inner_simplex.calc_division_lines(inner_corner_no, p_outer_simplex);
         
         if (division_line_arr[inner_corner_no] == null)
            {
            System.out.println("Simplex.cutout_from: division line is null");
            ShapeTileSimplex[] result = new ShapeTileSimplex[1];
            result[0] = p_outer_simplex;
            return result;
            }
         }
      
      boolean check_cross_first_line = false;
      PlaLineInt prev_division_line = null;
      PlaLineInt first_division_line = division_line_arr[0][0];
      PlaDirection first_direction = first_division_line.direction();
      Collection<ShapeTileSimplex> result_list = new LinkedList<ShapeTileSimplex>();

      for (int inner_corner_no = 0; inner_corner_no < inner_corner_count; ++inner_corner_no)
         {
         PlaLineInt next_division_line;
         if (inner_corner_no == inner_simplex.lines_size() - 1)
            next_division_line = division_line_arr[0][0];
         else
            next_division_line = division_line_arr[inner_corner_no + 1][0];
         PlaLineInt[] curr_division_lines = division_line_arr[inner_corner_no];
         if (curr_division_lines.length == 2)
            {
            // 2 division lines are nessesary (sharp corner).
            // Construct an unbounded simplex from curr_division_lines[1] and curr_division_lines[0]
            // and intersect it with the outer simplex
            PlaDirection curr_dir = curr_division_lines[0].direction();
            boolean merge_prev_division_line = false;
            boolean merge_first_division_line = false;
            if (prev_division_line != null)
               {
               PlaDirection prev_dir = prev_division_line.direction();
               if (curr_dir.determinant(prev_dir) > 0)

                  {
                  // the previous division line may intersect
                  // curr_division_lines[0] inside p_divide_simplex
                  merge_prev_division_line = true;
                  }
               }
            if (!check_cross_first_line)
               {
               check_cross_first_line = (inner_corner_no > 0 && curr_dir.determinant(first_direction) > 0);
               }
            if (check_cross_first_line)
               {
               PlaDirection curr_dir2 = curr_division_lines[1].direction();
               if (curr_dir2.determinant(first_direction) < 0)
                  {
                  // The current piece has an intersection area with the first piece.
                  // Add a line to tmp_polyline to prevent this
                  merge_first_division_line = true;
                  }
               }
            int piece_line_count = 2;
      
            if (merge_prev_division_line)
               ++piece_line_count;
            
            if (merge_first_division_line)
               ++piece_line_count;
            
            PlaLineIntAlist piece_lines = new PlaLineIntAlist(piece_line_count);
            piece_lines.add( new PlaLineInt(curr_division_lines[1].point_b, curr_division_lines[1].point_a) );
            piece_lines.add( curr_division_lines[0] );

            if (merge_prev_division_line)
               {
               piece_lines.add( prev_division_line );
               }
            
            if (merge_first_division_line)
               {
               piece_lines.add( new PlaLineInt(first_division_line.point_b, first_division_line.point_a));
               }
            
            ShapeTileSimplex curr_piece = new ShapeTileSimplex(piece_lines);
         
            result_list.add(curr_piece.intersection(p_outer_simplex));
            }
         
         
         // construct an unbounded simplex from next_division_line,
         // inner_simplex.line [inner_corner_no] and the last current division line
         // and intersect it with the outer simplex
         boolean merge_next_division_line = !next_division_line.point_b.equals(next_division_line.point_a);
         PlaLineInt last_curr_division_line = curr_division_lines[curr_division_lines.length - 1];
         PlaDirection last_curr_dir = last_curr_division_line.direction();
         boolean merge_last_curr_division_line = !last_curr_division_line.point_b.equals(last_curr_division_line.point_a);
         boolean merge_prev_division_line = false;
         boolean merge_first_division_line = false;
         if (prev_division_line != null)
            {
            PlaDirection prev_dir = prev_division_line.direction();
            if (last_curr_dir.determinant(prev_dir) > 0)
               {
               // the previous division line may intersect
               // the last current division line inside p_divide_simplex
               merge_prev_division_line = true;
               }
            }
         
         if (!check_cross_first_line)
            {
            check_cross_first_line = inner_corner_no > 0 && last_curr_dir.determinant(first_direction) > 0 && last_curr_dir.projection_value(first_direction)  < 0;
            // scalar_product checked to ignore backcrossing at small inner_corner_no
            }
         
         if (check_cross_first_line)
            {
            PlaDirection next_dir = next_division_line.direction();
            if (next_dir.determinant(first_direction) < 0)
               {
               // The current piece has an intersection area with the first piece. Add a line to tmp_polyline to prevent this
               merge_first_division_line = true;
               }
            }
         
         ArrayList<PlaLineInt> piece_lines = new ArrayList<PlaLineInt>(6);   // this is the possible max
         
         PlaLineInt curr_line = inner_simplex.tline_get(inner_corner_no);
         
         piece_lines.add( new PlaLineInt(curr_line.point_b, curr_line.point_a) );
         
         if (merge_next_division_line)
            {
            piece_lines.add( new PlaLineInt(next_division_line.point_b, next_division_line.point_a) );
            }
         
         if (merge_last_curr_division_line)
            {
            piece_lines.add( last_curr_division_line );
            }
         
         if (merge_prev_division_line)
            {
            piece_lines.add( prev_division_line );
            }
         
         if (merge_first_division_line)
            {
            piece_lines.add( new PlaLineInt(first_division_line.point_b, first_division_line.point_a) );
            }
         
         ShapeTileSimplex curr_piece = new ShapeTileSimplex(piece_lines);
         result_list.add(curr_piece.intersection(p_outer_simplex));
         next_division_line = prev_division_line;
         }
      ShapeTileSimplex[] result = new ShapeTileSimplex[result_list.size()];
      Iterator<ShapeTileSimplex> it = result_list.iterator();
      for (int i = 0; i < result.length; ++i)
         {
         result[i] = it.next();
         }
      return result;
      }

   @Override   
   ShapeTileSimplex[] cutout_from(ShapeTileOctagon p_oct)
      {
      return cutout_from(p_oct.to_Simplex());
      }

   @Override   
   ShapeTileSimplex[] cutout_from(ShapeTileBox p_box)
      {
      return cutout_from(p_box.to_Simplex());
      }

   /**
    * Removes lines, which are redundant in the definition of the shape of this simplex. 
    * Assumes that the lines of this simplex are sorted.
    */
   ShapeTileSimplex remove_redundant_lines()
      {
      ArrayList<PlaLineInt> work_arr = new ArrayList<PlaLineInt>(lines_size());

      // copy the sorted lines of arr into line_arr while skipping multiple lines
      PlaLineInt prev = tline_get(0);
      work_arr.add(prev);
      
      for (int index = 1; index < lines_size(); ++index)
         {
         PlaLineInt a_line = tline_get(index);
         
         if (a_line.equals(prev)) continue;
         
         work_arr.add(a_line);
         prev = a_line;
         }

      int new_length = work_arr.size();
      
      PlaSide[] intersection_sides = new PlaSide[new_length];
      // precalculated array , on which side of this line the previous and the next line do intersect

      boolean try_again = new_length > 2;
      int index_of_last_removed_line = new_length;
      while (try_again)
         {
         try_again = false;
         int prev_ind = new_length - 1;
         int next_ind;
         PlaLineInt prev_line = work_arr.get(prev_ind);
         PlaLineInt curr_line = work_arr.get(0);
         PlaLineInt next_line;
         
         for (int cur_ind = 0; cur_ind < new_length; ++cur_ind)
            {
            if (cur_ind == new_length - 1)
               {
               next_ind = 0;
               }
            else
               {
               next_ind = cur_ind + 1;
               }
            
            next_line = work_arr.get(next_ind);

            boolean remove_line = false;
            PlaDirection prev_dir = prev_line.direction();
            PlaDirection next_dir = next_line.direction();
            long det = prev_dir.determinant(next_dir);
            if (det != 0) // prev_line and next_line are not parallel
               {
               if (intersection_sides[cur_ind] == null)
                  {
                  // intersection_sides [ind] not precalculated
                  intersection_sides[cur_ind] = curr_line.side_of_intersection(prev_line, next_line);
                  }
               if (det > 0)
                  {
                  // direction of next_line is bigger than direction of prev_line
                  // if the intersection of prev_line and next_line is on the left of curr_line, curr_line does not
                  // contribute to the shape of the simplex
                  remove_line = (intersection_sides[cur_ind] != PlaSide.ON_THE_LEFT);
                  }
               else
                  {
                  // direction of next_line is smaller than direction of prev_line

                  if (intersection_sides[cur_ind] == PlaSide.ON_THE_LEFT)
                     {
                     PlaDirection curr_dir = curr_line.direction();
                     if (prev_dir.determinant(curr_dir) > 0)
                     // direction of curr_line is bigger than direction of prev_line
                        {
                        // the halfplane defined by curr_line does not intersect
                        // with the simplex defined by prev_line and nex_line, hence this simplex must be empty
                        new_length = 0;
                        try_again = false;
                        break;
                        }
                     }
                  }
               }
            else
               {
               // prev_line and next_line are parallel

               if (prev_line.side_of(next_line.point_a) == PlaSide.ON_THE_LEFT)
               // prev_line is to the left of next_line, the halfplanes defined by prev_line and next_line do not intersect
                  {
                  new_length = 0;
                  try_again = false;
                  break;
                  }
               }
            
            
            if (remove_line)
               {
               try_again = true;
               --new_length;
               
               work_arr.remove(cur_ind);
               
               for (int index = cur_ind; index < new_length; ++index)
                  {
                  intersection_sides[index] = intersection_sides[index + 1];
                  }

               if (new_length < 3)
                  {
                  try_again = false;
                  break;
                  }
               
               // reset 3 precalculated intersection_sides
               if (cur_ind == 0)
                  {
                  prev_ind = new_length - 1;
                  }
               
               intersection_sides[prev_ind] = null;
               if (cur_ind >= new_length)
                  {
                  next_ind = 0;
                  }
               else
                  {
                  next_ind = cur_ind;
                  }
               intersection_sides[next_ind] = null;
               --cur_ind;
               index_of_last_removed_line = cur_ind;
               }
            else
               {
               prev_line = curr_line;
               prev_ind = cur_ind;
               }
            
            curr_line = next_line;

            if (!try_again && cur_ind >= index_of_last_removed_line)
               {
               // tried all lines without removing one
               break;
               }
            }
         }

      if (new_length == 2)
         {
         // this is weird, two lines, it measn it is not closed !!!
         if (work_arr.get(0).is_parallel(work_arr.get(1)))
            {
            if (work_arr.get(0).direction().equals(work_arr.get(1).direction()))
               {
               // one of the two remaining lines is redundant
               if (work_arr.get(1).side_of(work_arr.get(0).point_a) == PlaSide.ON_THE_LEFT)
                  {
//                  work_arr[0] = work_arr[1];
                  work_arr.remove(0);
                  }
               --new_length;
               }
            else
               {
               // the two remaining lines have opposite direction the simplex may be empty
               if (work_arr.get(1).side_of(work_arr.get(0).point_a) == PlaSide.ON_THE_LEFT)
                  {
                  new_length = 0;
                  }
               }
            }
         }

      if (new_length == lines_size())
         {
         return this; // nothing removed
         }
      
      if (new_length == 0)
         {
         return ShapeTileSimplex.EMPTY;
         }
      
      return new ShapeTileSimplex(work_arr);
      }

   @Override   
   public boolean intersects(ShapeTileBox p_box)
      {
      return intersects(p_box.to_Simplex());
      }

   @Override   
   public boolean intersects(ShapeTileOctagon p_octagon)
      {
      return intersects(p_octagon.to_Simplex());
      }

   @Override   
   public boolean intersects(ShapeCircle p_circle)
      {
      return p_circle.intersects(this);
      }

   /**
    * For each corner of this inner simplex 1 or 2 perpendicular projections onto lines of the outer simplex are constructed, so
    * that the resulting pieces after cutting out the inner simplex are convex. 2 projections may be nessesary at sharp angle
    * corners. Used in in the method cutout_from with parametertype Simplex.
    */
   private PlaLineInt[] calc_division_lines(int p_inner_corner_no, ShapeTileSimplex p_outer_simplex)
      {
      PlaLineInt curr_inner_line = tline_get(p_inner_corner_no);
      
      PlaLineInt prev_inner_line = tline_get(get_prev_index(p_inner_corner_no));
      
      PlaPointFloat intersection = curr_inner_line.intersection_approx(prev_inner_line);

      if ( intersection.is_NaN() )
         {
         System.out.println("Simplex.calc_division_lines: intersection expexted");
         return null;
         }
      
      PlaPointInt inner_corner = intersection.round();
      
      double c_tolerance = 0.0001;
      
      boolean is_exact = Math.abs(inner_corner.v_x - intersection.v_x) < c_tolerance && Math.abs(inner_corner.v_y - intersection.v_y) < c_tolerance;

      if (!is_exact)
         {
         // it is assumed, that the corners of the original inner simplex are
         // exact and the not exact corners come from the intersection of the inner simplex with the outer simplex.
         // Because these corners lie on the border of the outer simplex, no division is nessesary
         PlaLineInt[] result = new PlaLineInt[1];
         result[0] = prev_inner_line;
         return result;
         }
      
      PlaDirection first_projection_dir = PlaDirection.NULL;
      PlaDirection second_projection_dir = PlaDirection.NULL;
      PlaDirection prev_inner_dir = prev_inner_line.direction().opposite();
      PlaDirection next_inner_dir = curr_inner_line.direction();
      int outer_line_no = 0;

      // search the first outer line, so that the perpendicular projection of the inner corner onto this
      // line is visible from inner_corner to the left of prev_inner_line.

      double min_distance = Integer.MAX_VALUE;

      for (int ind = 0; ind < p_outer_simplex.lines_size(); ++ind)
         {
         PlaLineInt outer_line = p_outer_simplex.tline_get(outer_line_no);
         PlaDirection curr_projection_dir =  inner_corner.perpendicular_direction(outer_line);
         if (curr_projection_dir == PlaDirection.NULL)
            {
            PlaLineInt[] result = new PlaLineInt[1];
            result[0] = new PlaLineInt(inner_corner, inner_corner);
            return result;
            }
         
         boolean projection_visible = prev_inner_dir.determinant(curr_projection_dir) >= 0;
         
         if (projection_visible)
            {
            double curr_distance = Math.abs(outer_line.distance_signed(inner_corner.to_float()));
            boolean second_division_necessary = curr_projection_dir.determinant(next_inner_dir) < 0;
            // may occor at a sharp angle
            PlaDirection curr_second_projection_dir = curr_projection_dir;

            if (second_division_necessary)
               {
               // search the first projection_dir between curr_projection_dir
               // and next_inner_dir, that is visible from next_inner_line
               boolean second_projection_visible = false;
               int tmp_outer_line_no = outer_line_no;
               while (!second_projection_visible)
                  {
                  if (tmp_outer_line_no == p_outer_simplex.lines_size() - 1)
                     {
                     tmp_outer_line_no = 0;
                     }
                  else
                     {
                     ++tmp_outer_line_no;
                     }
                  curr_second_projection_dir = inner_corner.perpendicular_direction(p_outer_simplex.tline_get(tmp_outer_line_no));

                  if (curr_second_projection_dir == PlaDirection.NULL)
                     {
                     // inner corner is on outer_line
                     PlaLineInt[] result = new PlaLineInt[1];
                     result[0] = new PlaLineInt(inner_corner, inner_corner);
                     return result;
                     }
                  if (curr_projection_dir.determinant(curr_second_projection_dir) < 0)
                     {
                     // curr_second_projection_dir not found; the angle between curr_projection_dir and
                     // curr_second_projection_dir would be already bigger than 180 degree
                     curr_distance = Integer.MAX_VALUE;
                     break;
                     }

                  second_projection_visible = curr_second_projection_dir.determinant(next_inner_dir) >= 0;
                  }
               curr_distance += Math.abs(p_outer_simplex.tline_get(tmp_outer_line_no).distance_signed(inner_corner.to_float()));
               }
            if (curr_distance < min_distance)
               {
               min_distance = curr_distance;
               first_projection_dir = curr_projection_dir;
               second_projection_dir = curr_second_projection_dir;
               }
            }
         if (outer_line_no == p_outer_simplex.lines_size() - 1)
            {
            outer_line_no = 0;
            }
         else
            {
            ++outer_line_no;
            }
         }
      if (min_distance == Integer.MAX_VALUE)
         {
         System.out.println("Simplex.calc_division_lines: division not found");
         return null;
         }

      PlaLineInt[] result;
      if (first_projection_dir.equals(second_projection_dir))
         {
         result = new PlaLineInt[1];
         result[0] = new PlaLineInt(inner_corner, first_projection_dir);
         }
      else
         {
         result = new PlaLineInt[2];
         result[0] = new PlaLineInt(inner_corner, first_projection_dir);
         result[1] = new PlaLineInt(inner_corner, second_projection_dir);
         }
      
      return result;
      }


   }
