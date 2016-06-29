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
 *
 * ShoveViaAlgo.java
 *
 * Created on 12. Dezember 2005, 06:48
 *
 */

package board.algo;

import java.util.Collection;
import java.util.LinkedList;
import board.BrdFromSide;
import board.RoutingBoard;
import board.awtree.AwtreeShapeSearch;
import board.items.BrdAbit;
import board.items.BrdAbitVia;
import board.items.BrdAreaConduction;
import board.items.BrdItem;
import board.items.BrdTracep;
import board.varie.ShoveDrillResult;
import freert.planar.PlaPointFloat;
import freert.planar.PlaPointInt;
import freert.planar.PlaVectorInt;
import freert.planar.ShapeConvex;
import freert.planar.ShapeTile;
import freert.planar.ShapeTileBox;
import freert.planar.ShapeTileOctagon;
import freert.varie.NetNosList;
import freert.varie.TimeLimit;

/**
 *
 * Contains internal auxiliary functions of class RoutingBoard for shoving vias and pins
 *
 * @author Alfons Wirtz
 */
public final class AlgoMoveDrillItem
   {
   private final RoutingBoard r_board;
   
   public AlgoMoveDrillItem ( RoutingBoard p_board )
      {
      r_board = p_board;
      }
   
   
   private boolean is_stop_requested ( TimeLimit p_time_limit )
      {
      if (p_time_limit == null ) return false;
      
      return p_time_limit.is_stop_requested();
      }
   
   
   /**
    * checks, if p_drill_item can be translated by p_vector by shoving obstacle traces and vias aside, so that no clearance
    * violations occur.
    */
   public boolean check(BrdAbit p_drill_item, PlaVectorInt p_vector, int p_max_recursion_depth, int p_max_via_recursion_depth, Collection<BrdItem> p_ignore_items, TimeLimit p_time_limit)
      {
      if (is_stop_requested(p_time_limit)) return false;

      if (p_drill_item.is_shove_fixed()) return false;

      // Check, that p_drillitem is only connected to traces.
      Collection<BrdItem> contact_list = p_drill_item.get_normal_contacts();
      
      for (BrdItem curr_contact : contact_list)
         {
         if (!(curr_contact instanceof BrdTracep || curr_contact instanceof BrdAreaConduction))
            {
            return false;
            }
         }

      Collection<BrdItem> ignore_items = p_ignore_items == null ? new LinkedList<BrdItem>() :  p_ignore_items;
      
      ignore_items.add(p_drill_item);

      boolean attach_allowed = false;

      if (p_drill_item instanceof BrdAbitVia)
         {
         attach_allowed = ((BrdAbitVia) p_drill_item).attach_allowed;
         }
      
      AwtreeShapeSearch search_tree = r_board.search_tree_manager.get_default_tree();
      
      for (int curr_layer = p_drill_item.first_layer(); curr_layer <= p_drill_item.last_layer(); ++curr_layer)
         {
         int curr_ind = curr_layer - p_drill_item.first_layer();
         ShapeTile curr_shape = p_drill_item.get_tree_shape(search_tree, curr_ind);
      
         if (curr_shape == null) continue;

         ShapeConvex new_shape = (ShapeConvex) curr_shape.translate_by(p_vector);
         ShapeTile curr_tile_shape;

         curr_tile_shape = new_shape.bounding_octagon();
         
         BrdFromSide from_side = new BrdFromSide(p_drill_item.center_get(), curr_tile_shape);
         if (r_board.shove_pad_algo.check_forced_pad(
               curr_tile_shape, 
               from_side, 
               curr_layer, 
               p_drill_item.net_nos, 
               p_drill_item.clearance_idx(), 
               attach_allowed, 
               ignore_items, 
               p_max_recursion_depth,
               p_max_via_recursion_depth, 
               true, 
               p_time_limit) == ShoveDrillResult.NOT_DRILLABLE)
            {
            return false;
            }
         }
      
      return true;
      }

   /**
    * Translates p_drill_item by p_vector by shoving obstacle traces and vias aside, so that no clearance violations occur. If
    * p_tidy_region != null, it will be joined by the bounding octagons of the translated shapes.
    */
   public boolean insert(BrdAbit p_drill_item, PlaVectorInt p_vector, int p_max_recursion_depth, int p_max_via_recursion_depth, ShapeTileOctagon p_tidy_region )
      {
      if (p_drill_item.is_shove_fixed()) return false;

      boolean attach_allowed = false;
      if (p_drill_item instanceof BrdAbitVia)
         {
         attach_allowed = ((BrdAbitVia) p_drill_item).attach_allowed;
         }
      
      Collection<BrdItem> ignore_items = new LinkedList<BrdItem>();
      ignore_items.add(p_drill_item);
      AwtreeShapeSearch search_tree = r_board.search_tree_manager.get_default_tree();
      for (int curr_layer = p_drill_item.first_layer(); curr_layer <= p_drill_item.last_layer(); ++curr_layer)
         {
         int curr_ind = curr_layer - p_drill_item.first_layer();
         ShapeTile curr_shape = p_drill_item.get_tree_shape(search_tree, curr_ind);

         if (curr_shape == null) continue;

         ShapeConvex new_shape = (ShapeConvex) curr_shape.translate_by(p_vector);
         ShapeTile curr_tile_shape;

         curr_tile_shape = new_shape.bounding_octagon();
         
         if (p_tidy_region != null)
            {
            p_tidy_region = p_tidy_region.union(curr_tile_shape.bounding_octagon());
            }
         
         BrdFromSide from_side = new BrdFromSide(p_drill_item.center_get(), curr_tile_shape);
         if ( ! r_board.shove_pad_algo.forced_pad(
               curr_tile_shape, 
               from_side, 
               curr_layer, 
               p_drill_item.net_nos, 
               p_drill_item.clearance_idx(), 
               attach_allowed, 
               ignore_items, 
               p_max_recursion_depth,
               p_max_via_recursion_depth))
            {
            return false;
            }
         ShapeTileBox curr_bounding_box = curr_shape.bounding_box();
         for (int j = 0; j < 4; ++j)
            {
            r_board.changed_area_join(curr_bounding_box.corner_approx(j), curr_layer);
            }
         }
      p_drill_item.move_by(p_vector);
      return true;
      }

   /**
    * Shoves vias out of p_obstacle_shape. Returns false, if the database is damaged, so that an undo is necessary afterwards.
    */
   public boolean shove_vias(
         ShapeTile p_obstacle_shape, 
         BrdFromSide p_from_side, 
         int p_layer, 
         NetNosList p_net_no_arr, 
         int p_cl_type, 
         Collection<BrdItem> p_ignore_items, 
         int p_max_recursion_depth,
         int p_max_via_recursion_depth, 
         boolean p_copper_sharing_allowed)
      {
      AwtreeShapeSearch search_tree = r_board.search_tree_manager.get_default_tree();
      AlgoShoveTraceEntries shape_entries = new AlgoShoveTraceEntries(
            p_obstacle_shape, p_layer, p_net_no_arr, p_cl_type, p_from_side, r_board);
      Collection<BrdItem> obstacles = search_tree.find_overlap_items_with_clearance(p_obstacle_shape, p_layer, NetNosList.EMPTY, p_cl_type);

      if (!shape_entries.store_items(obstacles, false, p_copper_sharing_allowed))
         {
         return true;
         }
      if (p_ignore_items != null)
         {
         shape_entries.shove_via_list.removeAll(p_ignore_items);
         }
      if (shape_entries.shove_via_list.isEmpty())
         {
         return true;
         }
      double shape_radius = 0.5 * p_obstacle_shape.bounding_box().min_width();

      for (BrdAbitVia curr_via : shape_entries.shove_via_list)
         {
         if (curr_via.shares_net_no(p_net_no_arr)) continue;

         if (p_max_via_recursion_depth <= 0) return true;

         PlaPointInt[] try_via_centers = try_shove_via_points(p_obstacle_shape, p_layer, curr_via, p_cl_type, true);
         PlaPointInt new_via_center = null;
         double max_dist = 0.5 * curr_via.get_shape_on_layer(p_layer).bounding_box().max_width() + shape_radius;
         double max_dist_square = max_dist * max_dist;
         PlaPointInt curr_via_center = curr_via.center_get();
         PlaPointFloat check_via_center = curr_via_center.to_float();
         PlaVectorInt rel_coor = null;

         for (int index = 0; index < try_via_centers.length; ++index)
            {
            if (index != 0 ) continue;
            
            if ( check_via_center.distance_square(try_via_centers[index].to_float()) > max_dist_square) continue;
            
            LinkedList<BrdItem> ignore_items = new LinkedList<BrdItem>();
          
            if (p_ignore_items != null) ignore_items.addAll(p_ignore_items);

            rel_coor = try_via_centers[index].difference_by(curr_via_center);
            
            // No time limit here because the item database is already changed.
            
            boolean shove_ok = check(curr_via, rel_coor, p_max_recursion_depth, p_max_via_recursion_depth - 1, ignore_items, null);
            
            if (shove_ok)
               {
               new_via_center = try_via_centers[index];
               break;
               }
            }
         
         if (new_via_center == null) continue;
         
         if (!insert(curr_via, rel_coor, p_max_recursion_depth, p_max_via_recursion_depth - 1, null ))
            {
            return false;
            }
         }
      return true;
      }

   /**
    * Calculates possible new location for a via to shove outside p_obstacle_shape. if p_extended_check is true, more than 1
    * possible new locations are calculated. The function isused here and in ShoveTraceAlgo.check.
    */
   public PlaPointInt[] try_shove_via_points(ShapeTile p_obstacle_shape, int p_layer, BrdAbitVia p_via, int p_cl_class_no, boolean p_extended_check )
      {
      AwtreeShapeSearch search_tree = r_board.search_tree_manager.get_default_tree();
      ShapeTile curr_via_shape = p_via.get_tree_shape_on_layer(search_tree, p_layer);
      if (curr_via_shape == null)
         {
         return new PlaPointInt[0];
         }
      boolean is_int_octagon = p_obstacle_shape.is_IntOctagon();
      double clearance_value = r_board.get_clearance(p_cl_class_no, p_via.clearance_idx(), p_layer);
      double shove_distance;
      if (is_int_octagon)
         {
         shove_distance = 0.5 * curr_via_shape.bounding_box().max_width();
         if (!search_tree.is_clearance_compensation_used())
            {
            shove_distance += clearance_value;
            }
         }
      else
         {
         // a different algorithm is used for calculating the new via centers
         shove_distance = 0;
         if (!search_tree.is_clearance_compensation_used())
            {
            // enlarge p_obstacle_shape and curr_via_shape by half of the clearance value to syncronize
            // with the check algorithm in ShapeSearchTree.overlapping_tree_entries_with_clearance
            shove_distance += 0.5 * clearance_value;
            }
         }

      // The additional constant 2 is an empirical value for the tolerance in case of diagonal shoving.
      shove_distance += 2;

      PlaPointInt curr_via_center = p_via.center_get();
      
      PlaPointInt[] try_via_centers;

      int try_count = 1;
      
      if (is_int_octagon)
         {
         ShapeTileOctagon curr_offset_octagon = p_obstacle_shape.bounding_octagon().enlarge(shove_distance);
         
         if (p_extended_check) try_count = 4;

         try_via_centers = curr_offset_octagon.nearest_border_projections(curr_via_center, try_count);
         }
      else
         {
         ShapeTile curr_offset_shape = p_obstacle_shape.enlarge(shove_distance);
      
         if (!search_tree.is_clearance_compensation_used())
            {
            curr_via_shape = curr_via_shape.enlarge(0.5 * clearance_value);
            }
         if (p_extended_check)
            {
            try_count = 4;
            }
         PlaPointFloat[] shove_deltas = curr_offset_shape.nearest_relative_outside_locations(curr_via_shape, try_count);
         try_via_centers = new PlaPointInt[shove_deltas.length];
         
         for (int index = 0; index < try_via_centers.length; ++index)
            {
            PlaVectorInt curr_delta = shove_deltas[index].to_vector();
            try_via_centers[index] = curr_via_center.translate_by(curr_delta);
            }
         }
      
      return try_via_centers;
      }
   }
