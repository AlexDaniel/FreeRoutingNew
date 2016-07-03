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
package board;

import interactive.IteraBoard;
import interactive.IteraSettings;
import java.awt.Graphics;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import autoroute.ArtControl;
import autoroute.ArtEngine;
import autoroute.expand.ExpandCostFactor;
import autoroute.varie.ArtResult;
import board.algo.AlgoMoveDrillItem;
import board.algo.AlgoOptimizeVia;
import board.algo.AlgoPullTight;
import board.algo.AlgoShovePad;
import board.algo.AlgoShoveTrace;
import board.algo.AlgoShoveVia;
import board.awtree.AwtreeFindEntry;
import board.awtree.AwtreeManager;
import board.awtree.AwtreeObject;
import board.awtree.AwtreeShapeSearch;
import board.infos.BrdViaInfo;
import board.items.BrdAbit;
import board.items.BrdAbitPin;
import board.items.BrdAbitVia;
import board.items.BrdArea;
import board.items.BrdAreaConduction;
import board.items.BrdAreaObstacleComp;
import board.items.BrdAreaObstacleVia;
import board.items.BrdComponentOutline;
import board.items.BrdItem;
import board.items.BrdOutline;
import board.items.BrdTracep;
import board.varie.BrdChangedArea;
import board.varie.BrdKeepPoint;
import board.varie.BrdShoveObstacle;
import board.varie.BrdStopConnection;
import board.varie.ItemFixState;
import board.varie.ItemSelectionChoice;
import board.varie.ItemSelectionFilter;
import freert.graphics.GdiContext;
import freert.graphics.GdiDrawable;
import freert.host.HostCom;
import freert.host.ObserverItem;
import freert.host.ObserverItemVoid;
import freert.library.BrdLibrary;
import freert.library.LibPadstack;
import freert.main.Stat;
import freert.planar.PlaArea;
import freert.planar.PlaPoint;
import freert.planar.PlaPointFloat;
import freert.planar.PlaPointInt;
import freert.planar.PlaPointIntAlist;
import freert.planar.PlaSegmentInt;
import freert.planar.PlaShape;
import freert.planar.PlaVectorInt;
import freert.planar.Polyline;
import freert.planar.ShapeConvex;
import freert.planar.ShapeSegments;
import freert.planar.ShapeTile;
import freert.planar.ShapeTileBox;
import freert.planar.ShapeTileOctagon;
import freert.rules.BoardRules;
import freert.rules.RuleNet;
import freert.varie.NetNosList;
import freert.varie.ThreadStoppable;
import freert.varie.TimeLimit;
import freert.varie.TimeLimitStoppable;
import freert.varie.UndoObjectNode;
import freert.varie.UndoObjectStorable;
import freert.varie.UndoObjects;
import gui.varie.GuiResources;

/**
 * Contains higher level functions of a board
 * @author Alfons Wirtz
 */
public final class RoutingBoard implements java.io.Serializable
   {
   private static final long serialVersionUID = 1L;
   private static final String classname = "RoutingBoard.";
   private static final int s_PREVENT_ENDLESS_LOOP = 5;

   // List of items inserted into this board, may be less than all available components
   public final UndoObjects undo_items;
   // List of placed components on the board
   public final BrdComponents brd_components;
   // Class defining the rules for items to be inserted into this board. Contains for example the clearance matrix.
   public final BoardRules brd_rules;
   // The library containing pastack masks, packagages and other templates used on the board.
   public final BrdLibrary brd_library;
   // The layer structure of this board.
   public final BrdLayerStructure layer_structure;
   // bounding orthogonal rectangle of this board
   public final ShapeTileBox bounding_box;
   // For communication with a host system or host design file formats.
   public final HostCom host_com;

   
   
   // the biggest half width of all traces on the board, actually, calculated on the fly when inserting...
   private int max_trace_half_width = 1000;
   // the smallest half width of all traces on the board, actually, calculated on the fly when inserting...
   private int min_trace_half_width = 10000;

   // it is transient just because it is useless to save it
   public transient Stat stat;
   // observers are not implemented anyway
   public transient ObserverItem observers = new ObserverItemVoid();
   // Handles the search trees pointing into the items of this board, initialized on constructor
   public transient AwtreeManager search_tree_manager;
   // the rectangle, where the graphics may be not updated
   private transient  ShapeTileBox update_box = ShapeTileBox.EMPTY;
   // the area marked for optimizing the route 
   public transient BrdChangedArea changed_area = new BrdChangedArea();
   // an obstacle that prevent the possibility of shove
   private transient BrdShoveObstacle shove_obstacle;
   
   public transient AlgoShoveTrace shove_trace_algo;
   public transient AlgoShoveVia shove_via_algo;
   public transient AlgoMoveDrillItem move_drill_algo;
   public transient AlgoShovePad shove_pad_algo;
   public transient AlgoOptimizeVia optimize_via;


   /**
    * Creates a new instance of a routing Board with surrounding box p_bounding_box Rules contains the restrictions to obey when
    * inserting items. Among other things it may contain a clearance matrix.
    */
   public RoutingBoard(
         ShapeTileBox p_bounding_box, 
         BrdLayerStructure p_layer_structure, 
         ShapeSegments[] p_outline_shapes, 
         int p_outline_cl_class_no, 
         BoardRules p_rules, 
         HostCom p_host_com,
         Stat p_stat)
      {
      stat = p_stat;
      layer_structure = p_layer_structure;
      brd_rules = p_rules;
      brd_library = new BrdLibrary();
      undo_items = new UndoObjects();
      brd_components = new BrdComponents();
      host_com = p_host_com;
      bounding_box = p_bounding_box;
      search_tree_manager = new AwtreeManager(this);
      
      p_rules.nets.set_board(this);
      insert_outline(p_outline_shapes, p_outline_cl_class_no);

      shove_obstacle   = new BrdShoveObstacle();
   
      shove_trace_algo = new AlgoShoveTrace(this);  
      shove_via_algo   = new AlgoShoveVia(this);
      move_drill_algo  = new AlgoMoveDrillItem(this);
      shove_pad_algo   = new AlgoShovePad(this);
      optimize_via     = new AlgoOptimizeVia(this);
      }

   
   /**
    * Inserts a trace into the board, whose geometry is described by a Polyline. 
    * p_clearance_class is the index in the clearance_matix, which describes the required clearance restrictions to other items. 
    * Because no internal cleaning of items is done, the new inserted item can be returned.
    */
   public BrdTracep insert_trace_without_cleaning(Polyline p_polyline, int p_layer, int p_half_width, NetNosList p_net_no_arr, int p_clearance_class, ItemFixState p_fixed_state)
      {
      if ( ! p_polyline.is_valid() ) return null;

      BrdTracep new_trace = new BrdTracep(p_polyline, p_layer, p_half_width, p_net_no_arr, p_clearance_class, 0, p_fixed_state, this);
      
      if (new_trace.corner_first().equals(new_trace.corner_last()))
         {
         // this would be an "invalid" trace, and we zap it only if it is not a "FIXED" trace
         // I wonder if this is actually possibly useful, since there are tons of places in the code that check for the above...
         if (p_fixed_state.ordinal() < ItemFixState.USER_FIXED.ordinal()) return null;
         }
      
      insert_item(new_trace);
      
      if (new_trace.is_nets_normal())
         {
         max_trace_half_width = Math.max(max_trace_half_width, p_half_width);
         min_trace_half_width = Math.min(min_trace_half_width, p_half_width);
         }
      
      return new_trace;
      }

   /**
    * Inserts a trace into the board, whose geometry is described by a Polyline. 
    * p_clearance_class is the index in the clearance_matix, which describes the required clearance restrictions to other items.
    */
   public void insert_trace(Polyline p_polyline, int p_layer, int p_half_width, NetNosList p_net_no_arr, int p_clearance_class, ItemFixState p_fixed_state)
      {
      BrdTracep new_trace = insert_trace_without_cleaning(p_polyline, p_layer, p_half_width, p_net_no_arr, p_clearance_class, p_fixed_state);

      if (new_trace == null) return;

      new_trace.normalize(changed_area.get_area(p_layer));
      }

   /**
    * Inserts a trace into the board, whose geometry is described by an array of points, and cleans up the net.
    */
   public void insert_trace(PlaPointIntAlist p_points, int p_layer, int p_half_width, NetNosList p_net_no_arr, int p_clearance_class, ItemFixState p_fixed_state)
      {
      for (PlaPointInt a_point : p_points )
         {
         if ( bounding_box.contains(a_point) ) continue;

         System.err.println("LayeredBoard.insert_trace: input point out of range");
         }
      
      Polyline poly = new Polyline(p_points);
      
      insert_trace(poly, p_layer, p_half_width, p_net_no_arr, p_clearance_class, p_fixed_state);
      }

   /**
    * Inserts a via into the board. 
    * @param p_attach_allowed indicates, if the via may overlap with SMD pins of the same net.
    */
   public void insert_via(LibPadstack p_padstack, PlaPointInt p_center, NetNosList p_net_no_arr, int p_clearance_class, ItemFixState p_fixed_state, boolean p_attach_allowed)
      {
      BrdAbitVia new_via = new BrdAbitVia(p_padstack, p_center, p_net_no_arr, p_clearance_class, 0, 0, p_fixed_state, p_attach_allowed, this);
      insert_item(new_via);
      int from_layer = p_padstack.from_layer();
      int to_layer = p_padstack.to_layer();
      
      for (int index = from_layer; index < to_layer; ++index)
         {
         for (int curr_net_no : p_net_no_arr)
            {
            split_traces(p_center, index, curr_net_no);
            }
         }
      }

   /**
    * Inserts a pin into the board. p_pin_no is the number of this pin in the library package of its component (starting with 0).
    */
   public void insert_pin(int p_component_no, int p_pin_no, NetNosList p_net_no_arr, int p_clearance_class, ItemFixState p_fixed_state)
      {
      BrdAbitPin new_pin = new BrdAbitPin(p_component_no, p_pin_no, p_net_no_arr, p_clearance_class, 0, p_fixed_state, this);
      insert_item(new_pin);
      }

   /**
    * Inserts an obstacle into the board , whose geometry is described by a polygonyal shape, which may have holes.
    * This is most likely experimental stuff that has beun but not yet functional
    */
   public void insert_obstacle(PlaArea p_area, int p_layer, int p_clearance_class, ItemFixState p_fixed_state)
      {
      if (p_area == null)
         {
         System.out.println("BasicBoard.insert_obstacle: p_area is null");
         return;
         }
      BrdArea obs = new BrdArea(p_area, p_layer, PlaVectorInt.ZERO, 0, false, p_clearance_class, 0, 0, null, p_fixed_state, this);
      insert_item(obs);
      }

   /**
    * Inserts an obstacle belonging to a component into the board 
    * p_name is to identify the corresponding ObstacstacleArea in the component package.
    */
   public void insert_obstacle(
         PlaArea p_area, 
         int p_layer, 
         PlaVectorInt p_translation, 
         int p_rotation_in_degree, 
         boolean p_side_changed, 
         int p_clearance_class, 
         int p_component_no, 
         String p_name,
         ItemFixState p_fixed_state)
      {
      
      if (p_area == null)
         {
         System.out.println("BasicBoard.insert_obstacle: p_area is null");
         return;
         }
      
      BrdArea obs = new BrdArea(p_area, p_layer, p_translation, p_rotation_in_degree, p_side_changed, p_clearance_class, 0, p_component_no, p_name, p_fixed_state, this);
      insert_item(obs);
      }

   /**
    * Inserts an via obstacle area into the board , whose geometry is described by a polygonyal shape, which may have holes.
    */
   public void insert_via_obstacle(PlaArea p_area, int p_layer, int p_clearance_class, ItemFixState p_fixed_state)
      {
      if (p_area == null)
         {
         System.out.println("BasicBoard.insert_via_obstacle: p_area is null");
         return;
         }
      BrdAreaObstacleVia obs = new BrdAreaObstacleVia(p_area, p_layer, PlaVectorInt.ZERO, 0, false, p_clearance_class, 0, 0, null, p_fixed_state, this);
      insert_item(obs);
      }

   /**
    * Inserts an via obstacle belonging to a component into the board p_name is to identify the corresponding ObstacstacleArea in
    * the component package.
    */
   public void insert_via_obstacle(
         PlaArea p_area, 
         int p_layer, 
         PlaVectorInt p_translation, 
         int p_rotation_in_degree, 
         boolean p_side_changed, 
         int p_clearance_class, 
         int p_component_no,
         String p_name, 
         ItemFixState p_fixed_state)
      {
      if (p_area == null)
         {
         System.out.println("BasicBoard.insert_via_obstacle: p_area is null");
         return;
         }
      BrdAreaObstacleVia obs = new BrdAreaObstacleVia(p_area, p_layer, p_translation, p_rotation_in_degree, p_side_changed, p_clearance_class, 0, p_component_no, p_name, p_fixed_state, this);
      insert_item(obs);
      }

   /**
    * Inserts a component obstacle area into the board , whose geometry is described by a polygonyal shape, which may have holes.
    */
   public void insert_component_obstacle(PlaArea p_area, int p_layer, int p_clearance_class, ItemFixState p_fixed_state)
      {
      if (p_area == null)
         {
         System.out.println("BasicBoard.insert_component_obstacle: p_area is null");
         return;
         }
      BrdAreaObstacleComp obs = new BrdAreaObstacleComp(p_area, p_layer, PlaVectorInt.ZERO, 0, false, p_clearance_class, 0, 0, null, p_fixed_state, this);
      insert_item(obs);
      }

   /**
    * Inserts a component obstacle belonging to a component into the board. p_name is to identify the corresponding ObstacstacleArea
    * in the component package.
    */
   public void insert_component_obstacle(
         PlaArea p_area, 
         int p_layer, 
         PlaVectorInt p_translation, 
         int p_rotation_in_degree, 
         boolean p_side_changed, 
         int p_clearance_class,
         int p_component_no, 
         String p_name, 
         ItemFixState p_fixed_state)
      {
      if (p_area == null)
         {
         System.out.println("BasicBoard.insert_component_obstacle: p_area is null");
         return;
         }
      BrdAreaObstacleComp obs = new BrdAreaObstacleComp(
            p_area, 
            p_layer, 
            p_translation, 
            p_rotation_in_degree, 
            p_side_changed, 
            p_clearance_class, 
            0, 
            p_component_no, 
            p_name, 
            p_fixed_state,
            this);
      insert_item(obs);
      }

   /**
    * Inserts a component ouline into the board.
    */
   public void insert_component_outline(PlaShape p_shape, boolean p_is_front, PlaVectorInt p_translation, int p_rotate_degree, int p_component_no, ItemFixState p_fixed_state)
      {
      if (p_shape == null)
         {
         System.out.println("BasicBoard.insert_component_outline: p_shape is null");
         return;
         }
      if (!p_shape.is_bounded())
         {
         System.out.println("BasicBoard.insert_component_outline: p_shape is not bounded");
         return;
         }
      
      BrdComponentOutline outline = new BrdComponentOutline(p_shape, p_is_front, p_translation, p_rotate_degree, p_component_no, p_fixed_state, this);
      insert_item(outline);
      }

   /**
    * Inserts a conduction area into the board , whose geometry is described by a polygonyal shape, which may have holes. If
    * p_is_obstacle is false, it is possible to route through the conduction area with traces and vias of foreign nets.
    */
   public BrdAreaConduction insert_conduction_area(PlaArea p_area, int p_layer, NetNosList p_net_no_arr, int p_clearance_class, boolean p_is_obstacle, ItemFixState p_fixed_state)
      {
      if (p_area == null)
         {
         System.out.println("BasicBoard.insert_conduction_area: p_area is null");
         return null;
         }
      BrdAreaConduction cc_area = new BrdAreaConduction(p_area, p_layer, PlaVectorInt.ZERO, 0, false, p_net_no_arr, p_clearance_class, 0, 0, null, p_is_obstacle, p_fixed_state, this);
      insert_item(cc_area);
      return cc_area;
      }

   /**
    * Inserts an Outline into the board.
    */
   public BrdOutline insert_outline(ShapeSegments[] p_outline_shapes, int p_clearance_class_no)
      {
      BrdOutline result = new BrdOutline(p_outline_shapes, p_clearance_class_no, 0, this);
      insert_item(result);
      return result;
      }

   /**
    * Returns the outline of the board.
    */
   public BrdOutline get_outline()
      {
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      
      for (;;)
         {
         UndoObjectStorable curr_item = undo_items.read_next(it);
      
         if (curr_item == null)  break;

         if (curr_item instanceof BrdOutline)
            {
            return (BrdOutline) curr_item;
            }
         }
      return null;
      }

   /**
    * Removes an item from the board
    */
   public void remove_item(BrdItem p_item)
      {
      if (p_item == null) return;

      // mostly to help garbage collector, but whatever
      p_item.art_item_clear();
      
      search_tree_manager.remove(p_item);
      undo_items.delete(p_item);

      // let the observers synchronize the deletion
      observers.notify_deleted(p_item);
      }

   /**
    * looks, if an item with id_no p_id_no is on the board. Returns the found item or null, if no such item is found.
    */
   public BrdItem get_item(int p_id_no)
      {
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      for (;;)
         {
         BrdItem curr_item = (BrdItem) undo_items.read_next(it);

         if (curr_item == null)  break;
     
         if (curr_item.get_id_no() == p_id_no) return curr_item;
         }
      
      return null;
      }

   /**
    * Returns the list of all items on the board
    */
   public Collection<BrdItem> get_items()
      {
      Collection<BrdItem> result = new LinkedList<BrdItem>();
      Iterator<UndoObjectNode> iter = undo_items.start_read_object();
      for (;;)
         {
         BrdItem curr_item = (BrdItem) undo_items.read_next(iter);

         if (curr_item == null) break;
         
         result.add(curr_item);
         }
      
      return result;
      }

   /**
    * Returns all connectable items on the board containing p_net_no
    * Unfortunately Connectable is an interface that is not a subtype of BrdItem...
    */
   public Collection<BrdItem> get_connectable_items(int p_net_no)
      {
      Collection<BrdItem> result = new LinkedList<BrdItem>();
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      for (;;)
         {
         BrdItem curr_item = (BrdItem) undo_items.read_next(it);
         
         if (curr_item == null) break;

         if ( !(curr_item instanceof BrdConnectable)) continue;
         
         if ( ! curr_item.contains_net(p_net_no)) continue;
         
         result.add(curr_item);
         }

      return result;
      }

   /**
    * Returns the count of vias on the board belonging to this net.
    */
   public int get_via_count(int net_number)
      {
      int result = 0;
      
      Collection<BrdItem> net_items = get_connectable_items(net_number);
      
      for (BrdItem curr_item : net_items)
         {
         if (curr_item instanceof BrdAbitVia) result++;
         }
      
      return result;
      }
   
   /**
    * Returns the count of connectable items of the net with number p_net_no
    */
   public int connectable_item_count(int p_net_no)
      {
      int result = 0;
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      for (;;)
         {
         BrdItem curr_item = (BrdItem) undo_items.read_next(it);
      
         if (curr_item == null) break;
        
         if (curr_item instanceof BrdConnectable && curr_item.contains_net(p_net_no))
            {
            ++result;
            }
         }
      return result;
      }

   /**
    * Returns all items with the input component number
    */
   public Collection<BrdItem> get_component_items(int p_component_no)
      {
      Collection<BrdItem> result = new LinkedList<BrdItem>();
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      for (;;)
         {
         BrdItem curr_item = (BrdItem) undo_items.read_next(it);
         
         if (curr_item == null) break;

         if (curr_item.get_component_no() != p_component_no) continue;

         result.add(curr_item);
         }

      return result;
      }

   /**
    * Returns all pins with the input component number
    */
   public Collection<BrdAbitPin> get_component_pins(int p_component_no)
      {
      Collection<BrdAbitPin> result = new LinkedList<BrdAbitPin>();
      
      Iterator<UndoObjectNode> it = undo_items.start_read_object();

      for (;;)
         {
         BrdItem curr_item = (BrdItem) undo_items.read_next(it);
      
         if (curr_item == null) break;

         if ( curr_item.get_component_no() != p_component_no ) continue;
         
         if ( ! ( curr_item instanceof BrdAbitPin) ) continue;

         result.add((BrdAbitPin) curr_item);
         }

      return result;
      }

   /**
    * Returns the pin with the input component number and pin number, or null, if no such pinn exists.
    */
   public BrdAbitPin get_pin(int p_component_no, int p_pin_no)
      {
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      for (;;)
         {
         BrdItem curr_item = (BrdItem) undo_items.read_next(it);
         if (curr_item == null)
            {
            break;
            }
         if (curr_item.get_component_no() == p_component_no && curr_item instanceof BrdAbitPin)
            {
            BrdAbitPin curr_pin = (BrdAbitPin) curr_item;
            if (curr_pin.pin_no == p_pin_no)
               {
               return curr_pin;
               }
            }
         }
      return null;
      }

   /**
    * Removes the items in p_item_list that are not fixed
    */
   public void remove_items_unfixed(Collection<BrdItem> p_item_list )
      {
      for (BrdItem curr_item : p_item_list )
         {
         if ( curr_item.is_delete_fixed() || curr_item.is_user_fixed()) continue;

         remove_item(curr_item);
         }
      }

   
   /**
    * remove all items, fixed or unfixed
    * @param with_delete_fixed
    * @return true if all items have been removed from the list
    */
   public boolean remove_items(Collection<BrdItem> p_item_list, boolean with_delete_fixed )
      {
      boolean all_deleted = true;
      
      for (BrdItem curr_item : p_item_list )
         {
         if ( ! curr_item.can_delete(with_delete_fixed) )
            {
            // some items did not get delete
            all_deleted = false;
            continue;
            }

         remove_item(curr_item);
         }
      
      return all_deleted;
      }

   
   /**
    * Returns the list of all conduction areas on the board
    */
   public Collection<BrdAreaConduction> get_conduction_areas()
      {
      Collection<BrdAreaConduction> result = new LinkedList<BrdAreaConduction>();
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      for (;;)
         {
         UndoObjectStorable curr_item = undo_items.read_next(it);
         
         if (curr_item == null) break;

         if (curr_item instanceof BrdAreaConduction)
            {
            result.add((BrdAreaConduction) curr_item);
            }
         }
      return result;
      }

   /**
    * Returns the list of all pins on the board
    */
   public LinkedList<BrdAbitPin> get_pins()
      {
      LinkedList<BrdAbitPin> result = new LinkedList<BrdAbitPin>();

      Iterator<UndoObjectNode> iter = undo_items.start_read_object();
      
      for (;;)
         {
         UndoObjectStorable curr_item = undo_items.read_next(iter);

         if (curr_item == null) break;

         if ( ! ( curr_item instanceof BrdAbitPin) ) continue;

         result.add((BrdAbitPin) curr_item);
         }
      
      return result;
      }

   /**
    * Returns the list of all pins on the board with only 1 layer
    */
   public Collection<BrdAbitPin> get_smd_pins()
      {
      Collection<BrdAbitPin> result = new LinkedList<BrdAbitPin>();
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      for (;;)
         {
         UndoObjectStorable curr_item = undo_items.read_next(it);
         if (curr_item == null) break;

         if ( ! ( curr_item instanceof BrdAbitPin) ) continue;

         BrdAbitPin curr_pin = (BrdAbitPin) curr_item;
      
         if (curr_pin.first_layer() == curr_pin.last_layer())
            result.add(curr_pin);
         }

      return result;
      }

   /**
    * Returns the list of all vias on the board
    */
   public Collection<BrdAbitVia> get_vias()
      {
      Collection<BrdAbitVia> result = new LinkedList<BrdAbitVia>();
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      for (;;)
         {
         UndoObjectStorable curr_item = undo_items.read_next(it);

         if (curr_item == null) break;

         if (curr_item instanceof BrdAbitVia)
            result.add((BrdAbitVia) curr_item);

         }
      return result;
      }

   /**
    * Returns the list of all traces on the board
    */
   public Collection<BrdTracep> get_traces()
      {
      Collection<BrdTracep> result = new LinkedList<BrdTracep>();
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      for (;;)
         {
         UndoObjectStorable curr_item = undo_items.read_next(it);
         if (curr_item == null)  break;

         if (curr_item instanceof BrdTracep)
            result.add((BrdTracep) curr_item);

         }
      return result;
      }

   /**
    * Returns the cumulative length of all traces on the board
    */
   public final double cumulative_trace_length()
      {
      double result = 0;
      
      Iterator<UndoObjectNode> it = undo_items.start_read_object();

      for (;;)
         {
         UndoObjectStorable curr_item = undo_items.read_next(it);
      
         if (curr_item == null) break;

         if (curr_item instanceof BrdTracep)
            {
            result += ((BrdTracep) curr_item).get_length();
            }
         }
      
      return result;
      }
   
   /**
    * Combines the connected traces of this net, which have only 1 contact at the connection point. 
    * if p_net_no < 0 traces of all nets are combined.
    */
   public void combine_traces(int p_net_no)
      {
      for (int counter=0; counter<3; counter++)
         {
         boolean something_changed = false;

         Iterator<UndoObjectNode> iter = undo_items.start_read_object();
         
         for (;;)
            {
            BrdItem curr_item = (BrdItem) undo_items.read_next(iter);
      
            if (curr_item == null) break;
            
            if ( ! (curr_item  instanceof BrdTracep) ) continue;

            BrdTracep a_trace = (BrdTracep)curr_item;
            
            if ( ! a_trace.contains_net_wildcard(p_net_no)) continue;
            
            if ( ! a_trace.is_on_the_board() ) continue;
            
            something_changed |= a_trace.combine(20);
            }
         
         // if after combine nothing has changed then just get out
         if ( ! something_changed ) break;
         } 
      }

   /**
    * Normalizes the traces of this net
    * No return value since nobody is checking...
    */
   public void normalize_traces(int p_net_no)
      {
      // It was getting stuck, so, the simplest thing to do is to add a timeout, until the whole system is fixed
      long time_end = System.currentTimeMillis() + 20 * 1000;

      Iterator<UndoObjectNode> itera = undo_items.start_read_object();
      
      while ( System.currentTimeMillis() < time_end )
         {
         BrdItem curr_item = (BrdItem) undo_items.read_next(itera);
         
         // this just measn end of items.
         if (curr_item == null) break;
         
         if ( ! curr_item.is_on_the_board() ) continue;

         if ( ! curr_item.contains_net(p_net_no) ) continue;
         
         if ( ! ( curr_item instanceof BrdTracep ) ) continue;

         BrdTracep curr_trace = (BrdTracep) curr_item;
         
         // really, if a trace is user fixed we should not touch it
         if ( curr_trace.is_user_fixed() ) continue;
         
         if ( curr_trace.normalize(null) )
            {
            ;
            }
         else if ( remove_if_cycle(curr_trace))
            {
            ;
            }
         }
      }

   /**
    * Looks for traces of the input net on the input layer, so that p_location is on the trace polygon, and splits these traces.
    * @return false, if no trace was split.
    */
   public boolean split_traces(PlaPoint p_location, int p_layer, int p_net_no)
      {
      ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionChoice.TRACES);
      
      Collection<BrdItem> picked_items = pick_items(p_location, p_layer, filter);
      
      ShapeTileOctagon location_shape = p_location.to_box().bounding_octagon();

      boolean trace_split = false;
      
      for (BrdItem curr_item : picked_items)
         {
         BrdTracep curr_trace = (BrdTracep) curr_item;
      
         if ( ! curr_trace.contains_net(p_net_no)) continue;

         Collection<BrdTracep> split_pieces = curr_trace.split(location_shape);

         if (split_pieces.size() != 1) trace_split = true;
         }
      
      return trace_split;
      }

   /**
    * @return a Collection of Collections of items forming a connected set.
    */
   public Collection<Collection<BrdItem>> get_connected_sets(int p_net_no)
      {
      Collection<Collection<BrdItem>> result = new LinkedList<Collection<BrdItem>>();

      if (p_net_no <= 0) return result;
      
      SortedSet<BrdItem> items_to_handle = new TreeSet<BrdItem>();
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      for (;;)
         {
         BrdItem curr_item = (BrdItem) undo_items.read_next(it);
         if (curr_item == null) break;
       
         if (curr_item instanceof BrdConnectable && curr_item.contains_net(p_net_no))
            {
            items_to_handle.add(curr_item);
            }
         }
     
      Iterator<BrdItem> it2 = items_to_handle.iterator();
      
      while (it2.hasNext())
         {
         BrdItem curr_item = it2.next();
         Collection<BrdItem> next_connected_set = curr_item.get_connected_set(p_net_no);
         result.add(next_connected_set);
         items_to_handle.removeAll(next_connected_set);
         it2 = items_to_handle.iterator();
         }
      
      return result;
      }

   /**
    * Returns all SearchTreeObjects on layer p_layer, which overlap with p_shape. If p_layer < 0, the layer is ignored
    */
   public final Set<AwtreeObject> overlapping_objects(ShapeConvex p_shape, int p_layer)
      {
      return search_tree_manager.get_default_tree().find_overlap_objects(p_shape, p_layer, NetNosList.EMPTY);
      }

   /**
    * Returns items, which overlap with p_shape on layer p_layer inclusive clearance. 
    * p_clearance_class is the index in the clearance matrix, which describes the required clearance restrictions to other items. 
    * The function may also return items, which are nearly overlapping, but do not overlap with exact calculation. 
    * If p_layer < 0, the layer is ignored.
    */
   public Set<BrdItem> overlapping_items_with_clearance(ShapeTile p_shape, int p_layer, NetNosList p_ignore_net_nos, int p_clearance_class)
      {
      AwtreeShapeSearch default_tree = search_tree_manager.get_default_tree();
      return default_tree.find_overlap_items_with_clearance(p_shape, p_layer, p_ignore_net_nos, p_clearance_class);
      }

   /**
    * @Returns all items on layer p_layer, which overlap with p_area. If p_layer < 0, the layer is ignored
    */
   public Set<BrdItem> overlapping_items(ShapeTile p_area, int p_layer)
      {
      Set<BrdItem> result = new TreeSet<BrdItem>();
      
      ShapeTile[] tile_shapes = p_area.split_to_convex();
      
      for (int index = 0; index < tile_shapes.length; ++index)
         {
         Set<AwtreeObject> curr_overlaps = overlapping_objects(tile_shapes[index], p_layer);
         
         for (AwtreeObject curr_overlap : curr_overlaps)
            {
            if (curr_overlap instanceof BrdItem)  result.add((BrdItem) curr_overlap);
            }
         }
      return result;
      }

   /**
    * Checks, if the an object with shape p_shape and net nos p_net_no_arr and clearance class p_cl_class 
    * can be inserted on layer p_layer without clearance violation
    * WARNING apparently p_net_no_Arr is always empty, does not seems correct no ?
    * @return true if it can be inserted
    */
   public boolean check_shape(ShapeTile[] tiles, int p_layer, NetNosList p_net_no_arr, int p_cl_class)
      {
      AwtreeShapeSearch default_tree = search_tree_manager.get_default_tree();
      
      for (int index = 0; index < tiles.length; ++index)
         {
         ShapeTile curr_shape = tiles[index];
         
         if ( ! curr_shape.is_contained_in(bounding_box)) return false;
         
         Collection<AwtreeFindEntry> obstacles = default_tree.find_overlap_tree_entries_with_clearance(curr_shape, p_layer, p_net_no_arr, p_cl_class);
         
         for (AwtreeFindEntry cur_entry : obstacles)
            {
            AwtreeObject curr_ob = cur_entry.object;
            
            boolean is_obstacle = p_net_no_arr.is_obstacle(curr_ob);
             
            if (is_obstacle) return false;
            }
         }
      
      return true;
      }

   /**
    * Checks, if the a trace line with shape p_shape and net numbers p_net_no_arr and clearance class p_cl_class can be inserted on
    * layer p_layer without clearance violation. 
    * If p_contact_pins != null, all pins not contained in p_contact_pins are regarded as obstacles, even if they are of the own net.
    * @return true if it can be inserted without problems
    */
   public boolean check_trace (ShapeTile p_shape, int p_layer, NetNosList p_net_no_arr, int p_cl_class, Set<BrdAbitPin> p_contact_pins)
      {
      if (!p_shape.is_contained_in(bounding_box)) return false;
      
      AwtreeShapeSearch default_tree = search_tree_manager.get_default_tree();
      
      Collection<AwtreeFindEntry> tree_entries = default_tree.find_overlap_tree_entries_with_clearance(p_shape, p_layer, NetNosList.EMPTY, p_cl_class);
      
      for (AwtreeFindEntry curr_tree_entry : tree_entries)
         {
         if (!(curr_tree_entry.object instanceof BrdItem))  continue;
      
         BrdItem curr_item = (BrdItem) curr_tree_entry.object;

         if (p_contact_pins != null)
            {
            if (p_contact_pins.contains(curr_item)) continue;

            if (curr_item instanceof BrdAbitPin)
               {
               // The contact pins of the trace should be contained in p_ignore_items.
               // Other pins are handled as obstacles to avoid acid traps.
               return false;
               }
            }
         
         boolean is_obstacle = p_net_no_arr.is_trace_obstacle(curr_item);
    
         if (is_obstacle && (curr_item instanceof BrdTracep) && p_contact_pins != null)
            {
            // check for traces of foreign nets at tie pins, which will be ignored inside the pin shape
            ShapeTile intersection = null;

            for (BrdAbitPin curr_contact_pin : p_contact_pins)
               {
               if (curr_contact_pin.net_count() <= 1 || !curr_contact_pin.shares_net(curr_item)) continue;

               if (intersection == null)
                  {
                  ShapeTile obstacle_trace_shape = curr_item.tile_shape_get(curr_tree_entry.shape_index_in_object);
                  intersection = p_shape.intersection(obstacle_trace_shape);
                  }
               
               ShapeTile pin_shape = curr_contact_pin.get_tile_shape_on_layer(p_layer);
               if (pin_shape.contains_approx(intersection))
                  {
                  is_obstacle = false;
                  break;
                  }
               }
            }
         
         if (is_obstacle)  return false;
         }
      
      return true;
      }

   /**
    * Checks, if a polyline trace with the input parameters can be inserted without clearance violations
    */
   public boolean check_trace (Polyline p_polyline, int p_layer, int p_pen_half_width, NetNosList p_net_no_arr, int p_clearance_class)
      {
      BrdTracep tmp_trace = new BrdTracep(p_polyline, p_layer, p_pen_half_width, p_net_no_arr, p_clearance_class, 0, ItemFixState.UNFIXED, this);
      
      Set<BrdAbitPin> contact_pins = tmp_trace.touching_pins_at_end_corners();
      
      for (int index = 0; index < tmp_trace.tile_shape_count(); ++index)
         {
         if (!check_trace(tmp_trace.tile_shape_get(index), p_layer, p_net_no_arr, p_clearance_class, contact_pins))
            {
            return false;
            }
         }
      return true;
      }

   /**
    * Returns the layer count of this board.
    */
   public int get_layer_count()
      {
      return layer_structure.size();
      }

   
  
   
   /**
    * Draws all items of the board on their visible layers. Called in the overwritten paintComponent method of a class derived from
    * JPanel. The value of p_layer_visibility is expected between 0 and 1 for each layer.
    */
   public void draw(Graphics p_graphics, GdiContext p_graphics_context)
      {
      if (p_graphics_context == null) return;

      // draw all items on the board
      for (int curr_priority = GdiDrawable.MIN_DRAW_PRIORITY; curr_priority <= GdiDrawable.MIDDLE_DRAW_PRIORITY; ++curr_priority)
         {
         Iterator<UndoObjectNode> iter = undo_items.start_read_object();
         
         for (;;)
            {
            try
               {
               BrdItem curr_item = (BrdItem) undo_items.read_next(iter);
         
               if (curr_item == null) break;

               if ( curr_item.get_draw_priority() != curr_priority) continue;

               curr_item.draw(p_graphics, p_graphics_context);
               }
            catch (ConcurrentModificationException exc)
               {
               // may happen when window are changed interactively while running a logfile
               return;
               }
            }
         }
      }

   /**
    * Returns the list of items on the board, whose shape on layer p_layer contains the point at p_location. 
    * If p_layer < 0, the layer is ignored. 
    * @param p_item_selection_filter mst be non null
    */
   public Set<BrdItem> pick_items(PlaPoint p_location, int p_layer, ItemSelectionFilter p_filter)
      {
      Set<BrdItem> result = new TreeSet<BrdItem>();

      ShapeTileBox point_shape = p_location.to_box();
      
      Collection<AwtreeObject> overlaps = overlapping_objects(point_shape, p_layer);

      for (AwtreeObject curr_object : overlaps)
         {
         if ( !(curr_object instanceof BrdItem)) continue;

         BrdItem curr_item = (BrdItem) curr_object;
         
         if ( ! curr_item.is_selected_by_filter(p_filter) ) continue;
         
         result.add(curr_item);
         }

      return result;
      }

   public Set<BrdItem> pick_items(PlaPoint p_location, int p_layer )
      {
      Set<BrdItem> result = new TreeSet<BrdItem>();
   
      ShapeTileBox point_shape = p_location.to_box();
      
      Collection<AwtreeObject> overlaps = overlapping_objects(point_shape, p_layer);
   
      for (AwtreeObject curr_object : overlaps)
         {
         if ( !(curr_object instanceof BrdItem)) continue;
   
         BrdItem curr_item = (BrdItem) curr_object;
         
         result.add(curr_item);
         }
   
      return result;
      }

   
   
   
   /**
    * checks, if p_point is contained in the bounding box of this board.
    */
   public boolean contains(PlaPointInt p_point)
      {
      return p_point.is_contained_in(bounding_box);
      }

   /**
    * Returns the minimum clearance requested between items of clearance class p_class_1 and p_class_2. 
    * p_class_1 and p_class_2 are indices in the clearance matrix.
    * @return zero if some sort of error
    */
   public int get_clearance(int p_class_1, int p_class_2, int p_layer)
      {
      if ( brd_rules.clearance_matrix == null)
         {
         System.err.println(classname+"get_clearance: clearance matrix are anull");
         return 0;
         }

      return brd_rules.clearance_matrix.value_at(p_class_1, p_class_2, p_layer);
      }

   /**
    * returns the biggest half width of all traces on the board.
    */
   public int get_max_trace_half_width()
      {
      return max_trace_half_width;
      }

   /**
    * returns the smallest half width of all traces on the board.
    */
   public int get_min_trace_half_width()
      {
      return min_trace_half_width;
      }

   /**
    * Returns a surrounding box of the geometry of this board
    */
   public ShapeTileBox get_bounding_box()
      {
      return bounding_box;
      }

   /**
    * Returns a box containing all items in p_item_list.
    */
   public ShapeTileBox get_bounding_box(Collection<BrdItem> p_item_list)
      {
      ShapeTileBox result = ShapeTileBox.EMPTY;
      
      for (BrdItem curr_item : p_item_list)
         {
         result = result.union(curr_item.bounding_box());
         }
      
      return result;
      }

   /**
    * Resets the rectangle, where a graphics update is needed.
    */
   public void reset_graphics_update_box()
      {
      update_box = ShapeTileBox.EMPTY;
      }

   /**
    * Gets the rectangle, where a graphics update is needed on the screen.
    */
   public ShapeTileBox gdi_update_get()
      {
      return update_box;
      }

   /**
    * enlarges the graphics update box, so that it contains p_box
    */
   public void gdi_update_join(ShapeTileBox p_box)
      {
      if ( p_box == null ) return;
      
      update_box = update_box.union(p_box);
      }

   /**
    * starts notifying the observers of any change in the objects list
    */
   public void start_notify_observers()
      {
      observers.activate();
      }

   /**
    * ends notifying the observers of changes in the objects list
    */
   public void end_notify_observers()
      {
      observers.deactivate();
      }

   /**
    * Turns an obstacle area into a conduction area with net number p_net_no 
    * If it is convex and has no holes, it is turned into a Pin, alse into a conduction area.
    */
   public BrdConnectable make_conductive(BrdArea p_area, int p_net_no)
      {
      BrdItem new_item;
      PlaArea curr_area = p_area.get_relative_area();
      int layer = p_area.get_layer();
      ItemFixState fixed_state = p_area.get_fixed_state();
      PlaVectorInt translation = p_area.get_translation();
      int deg_rotation = p_area.get_rotation_in_degree();
      boolean side_changed = p_area.get_side_changed();

      NetNosList net_no_arr = new NetNosList(p_net_no);
      
      new_item = new BrdAreaConduction(
            curr_area, 
            layer, 
            translation, 
            deg_rotation, 
            side_changed, 
            net_no_arr, 
            p_area.clearance_idx(), 
            0, 
            p_area.get_component_no(), 
            p_area.area_name, 
            true, 
            fixed_state,
            this);
      remove_item(p_area);
      insert_item(new_item);
      return (BrdConnectable) new_item;
      }

   /**
    * Inserts an item into the board data base
    */
   public void insert_item(BrdItem p_item)
      {
      if (p_item == null) return;

      if ( p_item.clearance_idx() < 0 || p_item.clearance_idx() >= brd_rules.clearance_matrix.get_class_count())
         {
         System.err.println(classname+"insert_item: clearance_class no out of range");
         p_item.set_clearance_idx(0);
         }
      
      p_item.r_board = this;

      // clear up possible stored autoroute information
      p_item.art_item_clear();
      
      undo_items.insert(p_item);
      
      search_tree_manager.insert(p_item);

      observers.notify_new(p_item);
      }

   /**
    * Restores the situation at the previous snapshot.
    * Returns false, if no more undo is possible. 
    * Puts the numbers of the changed nets into the set p_changed_nets, if p_changed_nets != null
    */
   public boolean undo(Set<Integer> p_changed_nets)
      {
      brd_components.undo(observers);
      
      Collection<UndoObjectStorable> cancelled_objects = new LinkedList<UndoObjectStorable>();
      Collection<UndoObjectStorable> restored_objects = new LinkedList<UndoObjectStorable>();
      
      boolean result = undo_items.undo(cancelled_objects, restored_objects);
      
      // update the search trees
      Iterator<UndoObjectStorable> iter = cancelled_objects.iterator();

      while (iter.hasNext())
         {
         BrdItem curr_item = (BrdItem) iter.next();

         search_tree_manager.remove(curr_item);

         // let the observers syncronize the deletion
         observers.notify_deleted(curr_item);
         
         if (p_changed_nets != null)
            {
            for (int i = 0; i < curr_item.net_count(); ++i)
               {
               p_changed_nets.add(new Integer(curr_item.get_net_no(i)));
               }
            }
         }
      
      iter = restored_objects.iterator();
      
      while (iter.hasNext())
         {
         BrdItem curr_item = (BrdItem) iter.next();
         
         curr_item.set_transient_field(this);
         curr_item.art_item_clear();
         
         search_tree_manager.insert(curr_item);

         // let the observers know the insertion
         observers.notify_new(curr_item);
         
         if (p_changed_nets != null)
            {
            for (int i = 0; i < curr_item.net_count(); ++i)
               {
               p_changed_nets.add(new Integer(curr_item.get_net_no(i)));
               }
            }
         }
      return result;
      }

   /**
    * Restores the situation before the last undo. 
    * Returns false, if no more redo is possible. 
    * Puts the numbers of the changed nets into the set p_changed_nets, if p_changed_nets != null
    */
   public boolean redo(Set<Integer> p_changed_nets)
      {
      brd_components.redo(observers);
      Collection<UndoObjectStorable> cancelled_objects = new LinkedList<UndoObjectStorable>();
      Collection<UndoObjectStorable> restored_objects = new LinkedList<UndoObjectStorable>();
      boolean result = undo_items.redo(cancelled_objects, restored_objects);
      // update the search trees
      Iterator<UndoObjectStorable> it = cancelled_objects.iterator();
      while (it.hasNext())
         {
         BrdItem curr_item = (BrdItem) it.next();
         search_tree_manager.remove(curr_item);
         // let the observers syncronize the deletion
         observers.notify_deleted(curr_item);
         if (p_changed_nets != null)
            {
            for (int i = 0; i < curr_item.net_count(); ++i)
               {
               p_changed_nets.add(curr_item.get_net_no(i));
               }
            }
         }
      it = restored_objects.iterator();
      while (it.hasNext())
         {
         BrdItem curr_item = (BrdItem) it.next();
         curr_item.r_board = this;
         search_tree_manager.insert(curr_item);
         curr_item.art_item_clear();
         // let the observers know the insertion
         observers.notify_new(curr_item);
         if (p_changed_nets != null)
            {
            for (int i = 0; i < curr_item.net_count(); ++i)
               {
               p_changed_nets.add(curr_item.get_net_no(i));
               }
            }
         }
      return result;
      }

   /**
    * Makes the current board situation restorable by undo.
    */
   public void generate_snapshot()
      {
      undo_items.generate_snapshot();
      brd_components.generate_snapshot();
      }

   /**
    * Removes the top snapshot from the undo stack, so that its situation cannot be restored any more. 
    * @return false, if no more snapshot could be popped.
    */
   public boolean pop_snapshot()
      {
      return undo_items.pop_snapshot();
      }

   /**
    * Looks if at the input position ends a trace with the input net number, which has no normal contact at that position. 
    * @return null, if no tail is found.
    */
   public BrdTracep get_trace_tail(PlaPoint p_location, int p_layer, NetNosList p_net_no_arr)
      {
      ShapeTileBox point_shape = p_location.to_box();
      
      Collection<AwtreeObject> found_items = overlapping_objects(point_shape, p_layer);

      for (AwtreeObject curr_ob : found_items )
         {
         if ( ! (curr_ob instanceof BrdTracep) ) continue;
         
         BrdTracep curr_trace = (BrdTracep) curr_ob;

         if (!curr_trace.nets_equal(p_net_no_arr))  continue;

         if (curr_trace.corner_first().equals(p_location))
            {
            Collection<BrdItem> contacts = curr_trace.get_start_contacts();
         
            if (contacts.size() == 0)  return curr_trace;
            }

         if (curr_trace.corner_last().equals(p_location))
            {
            Collection<BrdItem> contacts = curr_trace.get_end_contacts();

            if (contacts.size() == 0)  return curr_trace;
            }
         }

      return null;
      }

   /**
    * Checks, if p_trace is part of a cycle and remove it together with its connection in this case.
    */
   public boolean remove_if_cycle(BrdTracep p_trace)
      {
      if ( ! p_trace.is_on_the_board()) return false;

      if ( ! p_trace.has_cycle()) return false;
      
      // Remove tails at the endpoints after removing the cycle, if there was no tail before.
      
      int curr_layer = p_trace.get_layer();
      
      PlaPoint[] end_corners = new PlaPoint[2];
      end_corners[0] = p_trace.corner_first();
      end_corners[1] = p_trace.corner_last();
      
      boolean[] tail_at_endpoint_before = new boolean[2];
      
      for (int index = 0; index < 2; ++index)
         {
         BrdTracep tail = get_trace_tail(end_corners[index], curr_layer, p_trace.net_nos);
         tail_at_endpoint_before[index] = (tail != null);
         }
      
      Set<BrdItem> connection_items = p_trace.get_connection_items();
      
      remove_items_unfixed(connection_items);
      
      for (int index = 0; index < 2; ++index)
         {
         if ( tail_at_endpoint_before[index] ) continue;
         
         BrdTracep tail = get_trace_tail(end_corners[index], curr_layer, p_trace.net_nos);
         
         if (tail == null) continue;

         remove_items_unfixed(tail.get_connection_items());
         }

      return true;
      }


   private void readObject(ObjectInputStream p_stream) throws IOException, java.lang.ClassNotFoundException
      {
      p_stream.defaultReadObject();

      // restore all transient fields to a correct value
      update_box          = ShapeTileBox.EMPTY;
      search_tree_manager = new AwtreeManager(this);
      shove_trace_algo    = new AlgoShoveTrace(this);  
      shove_via_algo      = new AlgoShoveVia(this);
      move_drill_algo     = new AlgoMoveDrillItem(this);
      shove_pad_algo      = new AlgoShovePad(this);
      optimize_via        = new AlgoOptimizeVia(this);

      shove_obstacle      = new BrdShoveObstacle();
      observers           = new ObserverItemVoid();
      changed_area        = new BrdChangedArea();
      
      for ( BrdItem curr_item : get_items() )
         {
         // insert the items on the board into the search trees
         curr_item.set_transient_field(this); 

         search_tree_manager.insert(curr_item);
         }
      
      }
   
   public void set_transient_item ( IteraBoard p_itera_board )
      {
      brd_rules.set_transient_item(p_itera_board);
      stat = p_itera_board.get_stat();
      }
   
   /**
    * Removes the items in p_item_list and pulls the nearby rubber traces tight. 
    */
   public boolean remove_items_and_pull_tight(Collection<BrdItem> p_item_list, int p_pull_tight_accuracy, boolean p_with_delete_fixed)
      {
      boolean all_deleted = true;
      
      changed_area_clear();
      
      Set<Integer> changed_nets = new TreeSet<Integer>();

      for ( BrdItem curr_item : p_item_list )
         {
         if ( ! curr_item.can_delete(p_with_delete_fixed) )
            {
            all_deleted = false;
            continue;
            }
         
         for (int index = 0; index < curr_item.tile_shape_count(); ++index)
            {
            ShapeTile curr_shape = curr_item.tile_shape_get(index);
            changed_area.join(curr_shape, curr_item.shape_layer(index));
            }
         
         remove_item(curr_item);
         
         for (int index = 0; index < curr_item.net_count(); ++index)
            {
            changed_nets.add(curr_item.get_net_no(index));
            }
         }

      for (Integer curr_net_no : changed_nets)
         {
         combine_traces(curr_net_no);
         }
      
      TimeLimitStoppable t_limit = new TimeLimitStoppable(s_PREVENT_ENDLESS_LOOP);
      
      changed_area_optimize(NetNosList.EMPTY, p_pull_tight_accuracy, null, t_limit, null);
      
      return all_deleted;
      }

   /**
    * starts marking the changed areas for optimizing traces
    * In other words clear it up for future use
    */
   public void changed_area_clear()
      {
      changed_area.clear(get_layer_count());
      }

   /**
    * enlarges the changed area on p_layer, so that it contains p_point
    */
   public void changed_area_join(PlaPointFloat p_point, int p_layer)
      {
      changed_area.join(p_point, p_layer);
      }

   /**
    * Optimizes the route in the internally marked area. 
    * If p_net_no > 0, only traces with net number p_net_no are optimized. 
    * If p_clip_shape != null the optimizing is restricted to p_clip_shape. p_trace_cost_arr is used for optimizing vias and may be null. 
    * If p_stoppable_thread != null, the algorithm can be requested to be stopped.
    * If p_time_limit > 0; the algorithm will be stopped after p_time_limit Milliseconds. 
    * If p_keep_point != null, traces on layer p_keep_point_layer containing p_keep_point will also contain this point after optimizing.
    */
   public final void changed_area_optimize(NetNosList p_only_net_no_arr, int p_pullt_min_move, ExpandCostFactor[] p_trace_cost_arr, TimeLimitStoppable p_tlimit, BrdKeepPoint p_keep_point)
      {
      if ( changed_area.is_clear() ) return;
      
      AlgoPullTight pull_tight_algo = AlgoPullTight.get_instance(this, p_only_net_no_arr, p_pullt_min_move, p_tlimit, p_keep_point );
      
      pull_tight_algo.optimize_changed_area(p_trace_cost_arr);
      
      gdi_update_join(changed_area.surrounding_box());
      
      changed_area.clear(get_layer_count());
      }

   public final void changed_area_optimize(NetNosList p_only_net_no_arr, int p_pullt_min_move, ExpandCostFactor[] p_trace_cost_arr, TimeLimitStoppable p_tlimit )
      {
      changed_area_optimize(p_only_net_no_arr, p_pullt_min_move, p_trace_cost_arr, p_tlimit, null );
      }

   
   /** 
    * Checks if a rectangular boxed trace line segment with the input parameters can be inserted without conflict. 
    * If a conflict exists, The result length is the maximal line length from p_line.a to p_line.b, which can be inserted without conflict
    * (Integer.MAX_VALUE, if no conflict exists). If p_only_not_shovable_obstacles, unfixed traces and vias are ignored.
    */
   public final double check_trace (PlaPointInt p_from_point, PlaPointInt p_to_point, int p_layer, NetNosList p_net_no_arr, int p_trace_half_width, int p_cl_class_no, boolean p_only_not_shovable_obstacles)
      {
      if (p_from_point.equals(p_to_point)) return 0;
      
      PlaSegmentInt curr_line_segment = new PlaSegmentInt(p_from_point, p_to_point);
      
      return check_trace(curr_line_segment, p_layer, p_net_no_arr, p_trace_half_width, p_cl_class_no, p_only_not_shovable_obstacles);
      }

   /**
    * Checks if a trace shape around the input parameters can be inserted without conflict. 
    * If a conflict exists, The result length is the maximal line length from p_line.a to p_line.b, which can be inserted without conflict 
    * (Integer.MAX_VALUE, if no conflict exists) 
    * If p_only_not_shovable_obstacles, unfixed traces and vias are ignored.
    */
   public final double check_trace (PlaSegmentInt p_line_segment, int p_layer, NetNosList p_net_no_arr, int p_trace_half_width, int p_cl_class_no, boolean p_only_not_shovable_obstacles)
      {
      Polyline check_polyline = p_line_segment.to_polyline();
      
      ShapeTile shape_to_check = check_polyline.offset_shape(p_trace_half_width, 0);
      PlaPointFloat from_point = p_line_segment.start_point_approx();
      PlaPointFloat to_point   = p_line_segment.end_point_approx();
      
      double line_length = to_point.distance(from_point);
      double ok_length = Integer.MAX_VALUE;

      AwtreeShapeSearch default_tree = search_tree_manager.get_default_tree();

      Collection<AwtreeFindEntry> obstacle_list = default_tree.find_overlap_tree_entries_with_clearance(shape_to_check, p_layer, p_net_no_arr, p_cl_class_no);

      for (AwtreeFindEntry obstacle_entry : obstacle_list)
         {
         if ( ! (obstacle_entry.object instanceof BrdItem)) continue;

         BrdItem obstacle_item = (BrdItem) obstacle_entry.object;

         if ( p_only_not_shovable_obstacles && obstacle_item.is_route() && ! obstacle_item.is_shove_fixed()) continue;

         ShapeTile curr_obstacle_shape = obstacle_entry.object.get_tree_shape(default_tree, obstacle_entry.shape_index_in_object);
         ShapeTile curr_offset_shape;
         PlaPointFloat nearest_obstacle_point;
         double shorten_value;

         if (default_tree.is_clearance_compensation_used())
            {
            curr_offset_shape = shape_to_check;
            shorten_value = p_trace_half_width + brd_rules.clearance_matrix.clearance_compensation_value(obstacle_item.clearance_idx(), p_layer);
            }
         else
            {
            int clearance_value = get_clearance(obstacle_item.clearance_idx(), p_cl_class_no, p_layer);
            curr_offset_shape = shape_to_check.offset(clearance_value);
            shorten_value = p_trace_half_width + clearance_value;
            }
         
         ShapeTile intersection = curr_obstacle_shape.intersection(curr_offset_shape);
         
         if (intersection.is_empty()) continue;

         nearest_obstacle_point = intersection.nearest_point_approx(from_point);

         double projection = from_point.scalar_product(to_point, nearest_obstacle_point) / line_length;

         projection = Math.max(0.0, projection - shorten_value - 1);

         if (projection < ok_length)
            {
            ok_length = projection;

            if (ok_length <= 0)
               {
               return 0;
               }
            }
         }

      return ok_length;
      }

   /**
    * Checks, if p_item can be translated by p_vector without producing overlaps or clearance violations.
    */
   public final boolean check_item_move(BrdItem p_item, PlaVectorInt p_vector, Collection<BrdItem> p_ignore_items)
      {
      int net_count = p_item.net_nos.size();
      
      if (net_count > 1)
         {
         System.err.println(classname+"check_item_move: net_count > 1");
         return false; // not yet implemented
         }
      
      int contact_count = 0;
      
      // the connected items must remain connected after moving
      contact_count = p_item.get_all_contacts().size();
      
      if (p_item instanceof BrdTracep && contact_count > 0) return false;
   
      if (p_ignore_items != null) p_ignore_items.add(p_item);
      
      for (int index = 0; index < p_item.tile_shape_count(); ++index)
         {
         ShapeTile moved_shape = (ShapeTile) p_item.tile_shape_get(index).translate_by(p_vector);

         if (!moved_shape.is_contained_in(bounding_box)) return false;
         
         Set<BrdItem> obstacles = overlapping_items_with_clearance(
               moved_shape, p_item.shape_layer(index), p_item.net_nos, p_item.clearance_idx());

         for (BrdItem curr_item : obstacles)
            {
            if (p_ignore_items != null)
               {
               if (!p_ignore_items.contains(curr_item))
                  {
                  if (curr_item.is_obstacle(p_item)) return false;
                  }
               }
            else if (curr_item != p_item)
               {
               if (curr_item.is_obstacle(p_item)) return false;
               }
            }
         }
      
      return true;
      }

   /**
    * Checks, if the net number of p_item can be changed without producing clearance violations
    * @return true if all is fine
    */
   public boolean check_change_net(BrdItem p_item, int p_new_net_no)
      {
      NetNosList net_no_arr = new NetNosList(p_new_net_no);
      
      for (int index = 0; index < p_item.tile_shape_count(); ++index)
         {
         ShapeTile curr_shape = p_item.tile_shape_get(index);
         
         Set<BrdItem> obstacles = overlapping_items_with_clearance(curr_shape, p_item.shape_layer(index), net_no_arr, p_item.clearance_idx());

         for (BrdItem curr_ob : obstacles)
            {
            if ( curr_ob != p_item ) continue;
            
            // if the connectable contains the given net we are fine
            if ( curr_ob.contains_net(p_new_net_no) ) continue;

            return false;
            }
         }

      return true;
      }

   /**
    * Translates p_drill_item by p_vector and shoves obstacle traces aside. 
    * Returns false, if that was not possible without creating clearance violations. 
    * In this case the database may be damaged, so that an undo becomes necessary.
    */
   public boolean move_drill_item(
         BrdAbit p_drill_item, 
         PlaVectorInt p_vector, 
         int p_max_recursion_depth, 
         int p_max_via_recursion_depth, 
         int p_pull_tight_accuracy,
         int p_pull_tight_time_limit)
      {
      shove_fail_clear();
      
      // unfix the connected shove fixed traces.
      Collection<BrdItem> contact_list = p_drill_item.get_normal_contacts();
      Iterator<BrdItem> it = contact_list.iterator();
      while (it.hasNext())
         {
         BrdItem curr_contact = it.next();
         if (curr_contact.get_fixed_state() == ItemFixState.SHOVE_FIXED)
            {
            curr_contact.set_fixed_state(ItemFixState.UNFIXED);
            }
         }
      
      NetNosList net_no_arr = p_drill_item.net_nos;
      
      changed_area_clear();
      
      if (!move_drill_algo.insert(p_drill_item, p_vector, p_max_recursion_depth, p_max_via_recursion_depth, null))
         {
         return false;
         }
      
      NetNosList opt_net_no_arr = p_max_recursion_depth <= 0 ? net_no_arr : NetNosList.EMPTY;
      
      TimeLimitStoppable t_limit = new TimeLimitStoppable(p_pull_tight_time_limit);

      changed_area_optimize(opt_net_no_arr, p_pull_tight_accuracy, null, t_limit, null);
      
      return true;
      }

   /**
    * Checks, if there is an item near by sharing a net with p_net_no_arr, from where a routing can start
    * or where the routing can connect to. 
    * If p_from_item != null, items, which are connected to p_from_item, are ignored. 
    * Returns null, if no item is found, If p_layer < 0, the layer is ignored
    */
   public BrdItem pick_nearest_routing_item(PlaPointInt p_location, int p_layer, BrdItem p_from_item)
      {
      ShapeTile point_shape = new ShapeTileBox(p_location);

      Collection<BrdItem> found_items = overlapping_items(point_shape, p_layer);
      
      PlaPointFloat pick_location = p_location.to_float();
      
      double min_dist = Integer.MAX_VALUE;
      BrdItem nearest_item = null;
      Set<BrdItem> ignore_set = null;
      
      for (BrdItem curr_item : found_items )
         {
         if (!curr_item.is_connectable()) continue;
         
         boolean candidate_found = false;
         double curr_dist = 0;

         if (curr_item instanceof BrdTracep)
            {
            BrdTracep curr_trace = (BrdTracep) curr_item;
            
            if (p_layer < 0 || curr_trace.get_layer() == p_layer)
               {
               if (nearest_item instanceof BrdAbit) continue; // prefer drill items

               int trace_radius = curr_trace.get_half_width();
               curr_dist = curr_trace.polyline().distance(pick_location);
               if (curr_dist < min_dist && curr_dist <= trace_radius)
                  {
                  candidate_found = true;
                  }
               }
            }
         else if (curr_item instanceof BrdAbit)
            {
            BrdAbit curr_drill_item = (BrdAbit) curr_item;
            
            if (p_layer < 0 || curr_drill_item.is_on_layer(p_layer))
               {
               PlaPointFloat drill_item_center = curr_drill_item.center_get().to_float();
               curr_dist = drill_item_center.distance(pick_location);
               if (curr_dist < min_dist || nearest_item instanceof BrdTracep)
                  {
                  candidate_found = true;
                  }
               }
            }
         else if (curr_item instanceof BrdAreaConduction)
            {
            BrdAreaConduction curr_area = (BrdAreaConduction) curr_item;
            if ((p_layer < 0 || curr_area.get_layer() == p_layer) && nearest_item == null)
               {
               candidate_found = true;
               curr_dist = Integer.MAX_VALUE;
               }
            }
         
         if ( ! candidate_found ) continue;

         if (p_from_item != null)
            {
            if (ignore_set == null)
               {
               // calculated here to avoid unnessery calculations for performance reasons.
               ignore_set = p_from_item.get_connected_set(-1);
               }
            
            if (ignore_set.contains(curr_item)) continue;
            }

         min_dist = curr_dist;
         nearest_item = curr_item;
         }
      
      return nearest_item;
      }

   /**
    * Shoves aside traces, so that a via with the input parameters can be inserted without clearance violations. If the shove
    * failed, the database may be damaged, so that an undo becomes necessesary.
    * 
    * @return false, if the forced via failed.
    */
   public final boolean insert_via(
         BrdViaInfo p_via_info, 
         PlaPointInt p_location, 
         NetNosList p_net_no_arr, 
         int p_trace_clearance_class_no, 
         int[] p_trace_pen_halfwidth_arr, 
         int p_max_recursion_depth,
         int p_max_via_recursion_depth, 
         int p_pull_tight_accuracy, 
         int p_pull_tight_time_limit)
      {
      shove_fail_clear();

      changed_area_clear();

      boolean r_ok = shove_via_algo.shove_via_insert(
            p_via_info, 
            p_location, 
            p_net_no_arr, 
            p_trace_clearance_class_no, 
            p_trace_pen_halfwidth_arr, 
            p_max_recursion_depth, 
            p_max_via_recursion_depth);
      
      if ( ! r_ok ) return false;
      
      NetNosList opt_net_no_arr = p_max_recursion_depth <= 0 ? p_net_no_arr : NetNosList.EMPTY;

      TimeLimitStoppable t_limit = new TimeLimitStoppable(s_PREVENT_ENDLESS_LOOP);

      changed_area_optimize(opt_net_no_arr, p_pull_tight_accuracy, null, t_limit, null);

      return true;
      }

   
   /**
    * Tries to insert a trace line with the input parameters from p_from_corner to p_to_corner while shoving aside obstacle traces and vias. 
    * Returns the last point between p_from_corner and p_to_corner, to which the shove succeeded. 
    * Returns null, if the check was inaccurate and an error occurred while inserting, so that the database may be damaged and an undo necessary.
    * p_search_tree is the shape search tree used in the algorithm.
    */
   public final PlaPointInt insert_trace (
         PlaPointInt p_from_corner, 
         PlaPointInt p_to_corner, 
         int p_half_width, 
         int p_layer, 
         NetNosList p_net_no_arr, 
         int p_clearance_class_no, 
         int p_max_recursion_depth,
         int p_max_via_recursion_depth, 
         int p_max_spring_over_recursion_depth, 
         int p_pull_tight_accuracy, 
         boolean p_with_check, 
         TimeLimit p_time_limit)
      {
      if (p_from_corner.equals(p_to_corner)) return p_to_corner;
      
      // Now, careful, polyline does NOT preserve corners, it creates new ones with the same value!!
      Polyline insert_polyline = new Polyline(p_from_corner, p_to_corner);
      
      PlaPointInt ok_point = insert_trace(
            insert_polyline, 
            p_half_width, 
            p_layer, 
            p_net_no_arr, 
            p_clearance_class_no,
            p_max_recursion_depth, 
            p_max_via_recursion_depth,
            p_max_spring_over_recursion_depth, 
            p_pull_tight_accuracy, 
            p_with_check, 
            p_time_limit);

      PlaPointInt result;
      
      // the following needs to be done since polyline does not preserve points, it should, really...
      
      if (ok_point == insert_polyline.corner_first())
         {
         result = p_from_corner;
         }
      else if (ok_point == insert_polyline.corner_last())
         {
         result = p_to_corner;
         }
      else
         {
         result = ok_point;
         }
         
      return result;
      }

   /**
    * Checks, if a trace polyline with the input parameters can be inserted while shoving aside obstacle traces and vias.
    */
   public final boolean check_trace (
         Polyline p_polyline, 
         int p_half_width, 
         int p_layer, 
         NetNosList p_net_no_arr, 
         int p_clearance_class_no, 
         int p_max_recursion_depth,
         int p_max_via_recursion_depth, 
         int p_max_spring_over_recursion_depth)
      {
      AwtreeShapeSearch search_tree = search_tree_manager.get_default_tree();
      int compensated_half_width = p_half_width + search_tree.get_clearance_compensation(p_clearance_class_no, p_layer);
      ArrayList<ShapeTile> trace_shapes = p_polyline.offset_shapes(compensated_half_width, 0, p_polyline.corner_count());

      for (int index = 0; index < trace_shapes.size(); ++index)
         {
         ShapeTile curr_trace_shape = trace_shapes.get(index);
         
         BrdFromSide from_side = new BrdFromSide(p_polyline, index + 1, curr_trace_shape);

         boolean check_shove_ok = shove_trace_algo.shove_trace_check(
               curr_trace_shape, 
               from_side, 
               null, 
               p_layer, 
               p_net_no_arr, 
               p_clearance_class_no, 
               p_max_recursion_depth, 
               p_max_via_recursion_depth,
               p_max_spring_over_recursion_depth, 
               null);

         if ( ! check_shove_ok) return false;
         }
      
      return true;
      }

   
   private final BrdTracep pick_one_trace (PlaPointInt from_corner, int p_layer, NetNosList p_net_no_arr, int p_half_width, int p_clearance_class_no )
      {
      ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionChoice.TRACES);
      
      Set<BrdItem> picked_items = pick_items(from_corner, p_layer, filter);
      
      for ( BrdItem an_item : picked_items )
         {
         BrdTracep curr_trace = (BrdTracep) an_item;

         if ( ! curr_trace.nets_equal(p_net_no_arr) ) continue; 

         if ( curr_trace.get_half_width() != p_half_width ) continue;
         
         if ( curr_trace.clearance_idx() != p_clearance_class_no ) continue;
         
         return curr_trace;
         }

      return null;
      }
   
   
   
   /**
    * Tries to insert a trace polyline with the input parameters from while shoving aside obstacle traces and vias. 
    * Returns the last corner on the polyline, to which the shove succeeded
    * Now, this enforces int points, but splitting creates new ones, in theory not at end points, but not really... argh...
    * @returns null if the check was inaccurate and an error occurred while inserting, so that the database may be damaged and an undo necessary.
    */
   public final PlaPointInt insert_trace(
         Polyline p_polyline, 
         int p_half_width, 
         int p_layer, 
         NetNosList p_net_no_arr, 
         int p_clearance_class_no, 
         int p_max_recursion_depth,
         int p_max_via_recursion_depth, 
         int p_max_spring_over_recursion_depth, 
         int p_pullt_min_move, 
         boolean p_with_check, 
         TimeLimit p_time_limit)
      {
      shove_fail_clear();
      
      PlaPoint from_corner_point = p_polyline.corner_first();
      
      if ( from_corner_point.is_rational() )
         {
         // So, a trace polyline has always a first corner int, right ?
         System.err.println(classname+"insert_trace: from_corner_point NOT int");
         return null;
         }      
      
      PlaPointInt from_corner = (PlaPointInt)from_corner_point; 
      
      PlaPoint to_corner_point = p_polyline.corner_last();

      if ( to_corner_point.is_rational() )
         {
         System.err.println(classname+"insert_trace: to_corner_point NOT int");
         // really. what is the point of returning this ? I did not actually route up to this !
         return from_corner;
         }
     
      PlaPointInt to_corner = (PlaPointInt)to_corner_point; 

      if (from_corner.equals(to_corner)) return to_corner;
      
      changed_area_clear();
      
      // Check, if there ends a item of the same net at p_from_corner.
      // If so, its geometry will be used to cut off dog ears of the check shape.
      
      BrdTracep picked_trace = pick_one_trace(from_corner, p_layer, p_net_no_arr, p_half_width, p_clearance_class_no);
      
      AwtreeShapeSearch search_tree = search_tree_manager.get_default_tree();
      int compensated_half_width = p_half_width + search_tree.get_clearance_compensation(p_clearance_class_no, p_layer);

      Polyline new_polyline = shove_trace_algo.spring_over_obstacles(p_polyline, compensated_half_width, p_layer, p_net_no_arr, p_clearance_class_no, null);

      if (new_polyline == null) return from_corner;

      Polyline combined_polyline = picked_trace == null ? new_polyline : new_polyline.combine(picked_trace.polyline());
      
      if ( ! combined_polyline.is_valid() ) return from_corner;
      
      int start_shape_no = combined_polyline.plaline_len() - new_polyline.plaline_len();
      // calculate the last shapes of combined_polyline for checking
      ArrayList<ShapeTile> trace_shapes = combined_polyline.offset_shapes(compensated_half_width, start_shape_no, combined_polyline.plaline_len(-1));
      
      final int trace_shapes_count = trace_shapes.size();
      int last_shape_no = trace_shapes_count;
      
      for (int index = 0; index < trace_shapes_count; ++index)
         {
         ShapeTile curr_trace_shape = trace_shapes.get(index);
         
         BrdFromSide from_side = new BrdFromSide(combined_polyline, combined_polyline.corner_count() - trace_shapes_count - 1 + index, curr_trace_shape);

         if (p_with_check)
            {
            boolean check_shove_ok = shove_trace_algo.shove_trace_check(
                  curr_trace_shape, 
                  from_side, 
                  null, 
                  p_layer, 
                  p_net_no_arr, 
                  p_clearance_class_no, 
                  p_max_recursion_depth, 
                  p_max_via_recursion_depth,
                  p_max_spring_over_recursion_depth, 
                  p_time_limit);

            if (!check_shove_ok)
               {
               last_shape_no = index;
               break;
               }
            }
         
         boolean insert_ok = shove_trace_algo.shove_trace_insert(
               curr_trace_shape, 
               from_side, 
               p_layer, 
               p_net_no_arr, 
               p_clearance_class_no, 
               null, 
               p_max_recursion_depth, 
               p_max_via_recursion_depth,
               p_max_spring_over_recursion_depth);

         if ( ! insert_ok) return null;
         }

      PlaPointInt new_corner = to_corner;
      
      if (last_shape_no < trace_shapes_count)
         {
         // the shove with index last_shape_no failed.
         // Sample the shove line to a shorter shove distance and try again.
         ShapeTile last_trace_shape = trace_shapes.get(last_shape_no);
         
         int sample_width = 2 * get_min_trace_half_width();
         PlaPointFloat last_corner = new_polyline.corner_approx(last_shape_no + 1);
         PlaPointFloat prev_last_corner = new_polyline.corner_approx(last_shape_no);
         double last_segment_length = last_corner.distance(prev_last_corner);
         if (last_segment_length > 100 * sample_width)
            {
            // to many cycles to sample
            return from_corner;
            }
         
         int shape_index = combined_polyline.corner_count() - trace_shapes_count - 1 + last_shape_no;
         
         if (last_segment_length > sample_width)
            {
            new_polyline = new_polyline.shorten(new_polyline.plaline_len( - (trace_shapes_count - last_shape_no - 1)), sample_width);
            
            PlaPoint new_last_corner_point = new_polyline.corner_last();
            
            if ( new_last_corner_point.is_rational() )
               {
               System.out.println("insert_trace: A IntPoint wanted");
               return from_corner;
               }
            
            new_corner = new_last_corner_point.round();
            
            if (picked_trace == null)
               {
               combined_polyline = new_polyline;
               }
            else
               {
               BrdTracep combine_trace = (BrdTracep) picked_trace;
               combined_polyline = new_polyline.combine(combine_trace.polyline());
               }
            
            if ( ! combined_polyline.is_valid() ) return new_corner;
            
            shape_index = combined_polyline.plaline_len(-3);
            last_trace_shape = combined_polyline.offset_shape(compensated_half_width, shape_index);
            }
         
         BrdFromSide from_side = new BrdFromSide(combined_polyline, shape_index, last_trace_shape);
         boolean check_shove_ok = shove_trace_algo.shove_trace_check(
               last_trace_shape, 
               from_side, 
               null, 
               p_layer, 
               p_net_no_arr, 
               p_clearance_class_no, 
               p_max_recursion_depth, 
               p_max_via_recursion_depth, 
               p_max_spring_over_recursion_depth, 
               p_time_limit);

         if (!check_shove_ok) return from_corner;

         boolean insert_ok = shove_trace_algo.shove_trace_insert(
               last_trace_shape, 
               from_side, 
               p_layer, 
               p_net_no_arr, 
               p_clearance_class_no, 
               null, 
               p_max_recursion_depth, 
               p_max_via_recursion_depth, 
               p_max_spring_over_recursion_depth);

         if (!insert_ok)
            {
            System.out.println("insert_trace: shove trace failed");
            return null;
            }
         }
      
      
      
      // insert the new trace segment
      for (int index = 0; index < new_polyline.corner_count(); ++index)
         {
         changed_area_join(new_polyline.corner_approx(index), p_layer);
         }
      
      BrdTracep new_trace = insert_trace_without_cleaning(new_polyline, p_layer, p_half_width, p_net_no_arr,p_clearance_class_no, ItemFixState.UNFIXED);
      
      new_trace.combine(20);
      
      NetNosList opt_net_no_arr = p_max_recursion_depth <= 0 ? p_net_no_arr : NetNosList.EMPTY;
      
      TimeLimitStoppable t_limit = new TimeLimitStoppable(10,null);
      
      AlgoPullTight pull_tight_algo = AlgoPullTight.get_instance(
            this, opt_net_no_arr, p_pullt_min_move,t_limit,new BrdKeepPoint( new_corner, p_layer) );

      // Remove evtl. generated cycles because otherwise pull_tight may not work correctly.
      if (new_trace.normalize(changed_area.get_area(p_layer)))
         {
         pull_tight_algo.split_traces_keep_point();

         // otherwise the new corner may no more be contained in the new trace after optimizing
         ItemSelectionFilter item_filter = new ItemSelectionFilter(ItemSelectionChoice.TRACES);
         Set<BrdItem> curr_picked_items = pick_items(new_corner, p_layer, item_filter);
         new_trace = null;
         if (!curr_picked_items.isEmpty())
            {
            BrdItem found_trace = curr_picked_items.iterator().next();
            if (found_trace instanceof BrdTracep)
               {
               new_trace = (BrdTracep) found_trace;
               }
            }
         }

      // To avoid, that a separate handling for moving backwards in the own trace line becomes necessesary, pull tight is called here.
      if ( new_trace != null) new_trace.pull_tight(pull_tight_algo);
      
      return new_corner;
      }

   /**
    * Routes automatically p_item to another item of the same net, to which it is not yet electrically connected. 
    * @return an enum of type AutorouteResult
    */
   public ArtResult autoroute(BrdItem p_item, IteraSettings p_settings, int p_via_costs, ThreadStoppable p_stoppable_thread )
      {
      if (!(p_item instanceof BrdConnectable) )
         {
         userPrintln(classname+"autoroute p_item NOT connectable "+p_item);
         return ArtResult.ALREADY_CONNECTED;
         }
      
      if ( p_item.net_count() == 0)
         {
         userPrintln(classname+"autoroute p_item has NO nets "+p_item);
         return ArtResult.ALREADY_CONNECTED;
         }

      if (p_item.net_count() > 1)
         {
         userPrintln(classname+"autoroute p_item net_count() > 1 NOT possible "+p_item);
         return ArtResult.ALREADY_CONNECTED;
         }
      
      int route_net_no = p_item.get_net_no(0);
      
      ArtControl ctrl_settings = new ArtControl(this, route_net_no, p_settings, p_via_costs, p_settings.autoroute_settings.get_trace_cost_arr());
      
      ctrl_settings.stop_remove_fanout_vias = false;
      
      Set<BrdItem> route_start_set = p_item.get_connected_set(route_net_no);
      
      RuleNet route_net = brd_rules.nets.get(route_net_no);
      
      if (route_net != null && route_net.contains_plane())
         {
         for (BrdItem curr_item : route_start_set)
            {
            if (curr_item instanceof board.items.BrdAreaConduction)
               {
               return ArtResult.ALREADY_CONNECTED; // already connected to plane
               }
            }
         }
      
      Set<BrdItem> route_dest_set = p_item.get_unconnected_set(route_net_no);
      
      if (route_dest_set.size() == 0)
         {
         return ArtResult.ALREADY_CONNECTED; // p_item is already routed.
         }

      TimeLimitStoppable t_limit = new TimeLimitStoppable(10, p_stoppable_thread);
      
      ArtEngine a_engine = new ArtEngine (this, p_item.get_net_no(0), ctrl_settings.trace_clearance_idx, t_limit);

      SortedSet<BrdItem> ripped_item_list = new TreeSet<BrdItem>();

      ArtResult result;
      
      try
         {
         result = a_engine.autoroute_connection(route_start_set, route_dest_set, ctrl_settings, ripped_item_list);
         }
      catch ( Exception exc )
         {
         userPrintln(classname+"autoroute_connection ",exc);
         result = ArtResult.EXCEPTION;
         }
      
      if (result == ArtResult.ROUTED)
         {
         changed_area_optimize(NetNosList.EMPTY, p_settings.trace_pullt_min_move, ctrl_settings.trace_costs, t_limit);
         }
      
      return result;
      }

   /**
    * Autoroute from the input pin until the first via, in case the pin and its connected set has only 1 layer. 
    * Ripup is allowed if p_ripup_costs is >= 0. 
    * Returns an enum of type AutorouteResult
    */
   public ArtResult fanout(BrdAbitPin p_pin, interactive.IteraSettings p_settings, int p_ripup_costs, ThreadStoppable p_stoppable )
      {
      if (p_pin.first_layer() != p_pin.last_layer() || p_pin.net_count() != 1)
         {
         return ArtResult.ALREADY_CONNECTED;
         }
      
      int pin_net_no = p_pin.get_net_no(0);
      int pin_layer = p_pin.first_layer();
      Set<BrdItem> pin_connected_set = p_pin.get_connected_set(pin_net_no);
      for (BrdItem curr_item : pin_connected_set)
         {
         if (curr_item.first_layer() != pin_layer || curr_item.last_layer() != pin_layer)
            {
            return ArtResult.ALREADY_CONNECTED;
            }
         }
      Set<BrdItem> unconnected_set = p_pin.get_unconnected_set(pin_net_no);
      if (unconnected_set.isEmpty())
         {
         return ArtResult.ALREADY_CONNECTED;
         }
      ArtControl ctrl_settings = new ArtControl(this, pin_net_no, p_settings);
      ctrl_settings.is_fanout = true;
      ctrl_settings.stop_remove_fanout_vias = false;
      if (p_ripup_costs >= 0)
         {
         ctrl_settings.ripup_allowed = true;
         ctrl_settings.ripup_costs = p_ripup_costs;
         }
      SortedSet<BrdItem> ripped_item_list = new TreeSet<BrdItem>();
      
      ArtEngine a_engine = new ArtEngine(this, pin_net_no, ctrl_settings.trace_clearance_idx, p_stoppable );
      
      ArtResult result = a_engine.autoroute_connection(pin_connected_set, unconnected_set, ctrl_settings, ripped_item_list);
      
      if (result == ArtResult.ROUTED)
         {
         TimeLimitStoppable t_limit = new TimeLimitStoppable(s_PREVENT_ENDLESS_LOOP, p_stoppable);
         changed_area_optimize(NetNosList.EMPTY, p_settings.trace_pullt_min_move, ctrl_settings.trace_costs, t_limit);
         }
      return result;
      }

   /**
    * Inserts a trace from p_from_point to the nearest point on p_to_trace. 
    * @return false, if that is not possible without clearance violation.
    */
   public final boolean connect_to_trace(PlaPointInt p_from_point, BrdTracep p_to_trace, int p_pen_half_width, int p_cl_type)
      {
      PlaPoint first_corner = p_to_trace.corner_first();

      PlaPoint last_corner = p_to_trace.corner_last();

      NetNosList net_no_arr = p_to_trace.net_nos;
      
      Polyline apoly = p_to_trace.polyline();
      
      // no connection line necessary
      if (apoly.contains(p_from_point)) return true;
      
      Polyline connection_line = apoly.projection_line(p_from_point);

      if ( connection_line == null || ! connection_line.is_valid() ) return false;

      int trace_layer = p_to_trace.get_layer();
      
      // if the trace cannot be inserted...
      if (!check_trace(connection_line, trace_layer, p_pen_half_width, net_no_arr, p_cl_type))  return false;
      
      if ( changed_area != null)
         {
         for (int index = 0; index < connection_line.corner_count(); ++index)
            {
            changed_area.join(connection_line.corner_approx(index), trace_layer);
            }
         }

      insert_trace(connection_line, trace_layer, p_pen_half_width, net_no_arr, p_cl_type, ItemFixState.UNFIXED);
      
      if (!p_from_point.equals(first_corner))
         {
         BrdTracep tail = get_trace_tail(first_corner, trace_layer, net_no_arr);
      
         if (tail != null && !tail.is_user_fixed())
            {
            remove_item(tail);
            }
         }
      
      if ( ! p_from_point.equals(last_corner))
         {
         BrdTracep tail = get_trace_tail(last_corner, trace_layer, net_no_arr);

         if (tail != null && !tail.is_user_fixed())
            {
            remove_item(tail);
            }
         }
      
      return true;
      }

   /**
    * Checks, if the list p_items contains traces, which have no contact at their start or end point. 
    * Trace with net number p_except_net_no are ignored.
    */
   public final boolean contains_trace_tails(Collection<BrdItem> p_items, NetNosList p_except_net_no_arr)
      {
      for ( BrdItem curr_ob : p_items )
         {
         if ( ! (curr_ob instanceof BrdTracep) ) continue;
         
         BrdTracep curr_trace = (BrdTracep) curr_ob;
      
         if ( curr_trace.nets_equal(p_except_net_no_arr)) continue;
         
         if (curr_trace.is_tail()) return true;
         }

      return false;
      }

   /**
    * Removes all trace tails of the input net. 
    * If p_net_no <= 0, the tails of all nets are removed.
    * Why is it removing some vias that are just autoroute ? damiano
    */
   public void remove_trace_tails(int p_net_no, BrdStopConnection p_stop_connection_option)
      {
      SortedSet<BrdItem> stub_set = new TreeSet<BrdItem>();
      Collection<BrdItem> board_items = get_items();

      for (BrdItem curr_item : board_items)
         {
         if (! curr_item.is_route()) continue;
         
         if (curr_item.net_count() != 1)  continue;
         
         if (p_net_no > 0 && curr_item.get_net_no(0) != p_net_no) continue;
         
         if (! curr_item.is_tail()) continue;
         
         if (curr_item instanceof BrdAbitVia)
            {
            if (p_stop_connection_option == BrdStopConnection.VIA)  continue;

            if (p_stop_connection_option == BrdStopConnection.FANOUT_VIA)
               {
               if (curr_item.is_fanout_via(null)) continue;
               }
            }
         
         stub_set.add(curr_item);
         }

      SortedSet<BrdItem> stub_connections = new TreeSet<BrdItem>();
      for (BrdItem curr_item : stub_set)
         {
         int item_contact_count = curr_item.get_normal_contacts().size();
         if (item_contact_count == 1)
            {
            stub_connections.addAll(curr_item.get_connection_items(p_stop_connection_option));
            }
         else
            {
            // the connected items are no stubs for example if a via is only connected on 1 layer but to several traces.
            stub_connections.add(curr_item);
            }
         }
      
      if (stub_connections.isEmpty()) return;
      
      remove_items_unfixed(stub_connections);
      
      combine_traces(p_net_no);
      }


   /**
    * Sets, if all conduction areas on the board are obstacles for route of foreign nets.
    */
   public final void set_conduction_is_obstacle(boolean p_value)
      {
      // the logic is inverted, anyway do something if there is a change
      if ( brd_rules.get_ignore_conduction() != p_value)  return; 
      
      boolean something_changed = false;
      
      Iterator<UndoObjectNode> it = undo_items.start_read_object();

      for (;;)
         {
         // Change the is_obstacle property of all conduction areas of the board.
         BrdItem curr_item = (BrdItem) undo_items.read_next(it);

         if (curr_item == null)  break;
         
         if ( ! ( curr_item instanceof BrdAreaConduction) ) continue;

         BrdAreaConduction curr_conduction_area = (BrdAreaConduction) curr_item;

         BrdLayer curr_layer = layer_structure.get(curr_conduction_area.get_layer());
         
         if (curr_layer.is_signal && curr_conduction_area.is_area_obstacle() != p_value)
            {
            curr_conduction_area.set_is_obstacle(p_value);
            something_changed = true;
            }
         }
      
      brd_rules.set_ignore_conduction( ! p_value);
      
      if (something_changed)
         {
         search_tree_manager.reinsert_tree_items();
         }
      }

   /**
    * Return true is something changed in the board
    * @return
    */
   private boolean reduce_nets_of_route_items_changed ()
      {
      boolean something_changed = false;
      
      Iterator<UndoObjectNode> it = undo_items.start_read_object();
      
      for (;;)
         {
         UndoObjectStorable curr_ob = undo_items.read_next(it);

         if (curr_ob == null) break;

         BrdItem curr_item = (BrdItem) curr_ob;

         if (curr_item.net_nos.size() <= 1 || curr_item.get_fixed_state() == ItemFixState.SYSTEM_FIXED) continue;

         if (curr_ob instanceof BrdAbitVia)
            {
            Collection<BrdItem> contacts = curr_item.get_normal_contacts();
            for (int curr_net_no : curr_item.net_nos)
               {
               for (BrdItem curr_contact : contacts)
                  {
                  if (!curr_contact.contains_net(curr_net_no))
                     {
                     curr_item.remove_from_net(curr_net_no);
                     something_changed = true;
                     break;
                     }
                  }

               if (something_changed) break;
               }
            }
         else if (curr_ob instanceof BrdTracep)
            {
            BrdTracep curr_trace = (BrdTracep) curr_ob;
            Collection<BrdItem> contacts = curr_trace.get_start_contacts();
            for (int i = 0; i < 2; ++i)
               {
               for (int curr_net_no : curr_item.net_nos)
                  {
                  boolean pin_found = false;
                  for (BrdItem curr_contact : contacts)
                     {
                     if (curr_contact instanceof BrdAbitPin)
                        {
                        pin_found = true;
                        if (!curr_contact.contains_net(curr_net_no))
                           {
                           curr_item.remove_from_net(curr_net_no);
                           something_changed = true;
                           break;
                           }
                        }
                     }
                  if (!pin_found) // at tie pins traces may have different nets
                     {
                     for (BrdItem curr_contact : contacts)
                        {
                        if (!(curr_contact instanceof BrdAbitPin) && !curr_contact.contains_net(curr_net_no))
                           {
                           curr_item.remove_from_net(curr_net_no);
                           something_changed = true;
                           break;
                           }
                        }
                     }
                  }
               
               if (something_changed) break;

               contacts = curr_trace.get_end_contacts();
               }
            
            if (something_changed) break;
            }

         if (something_changed) break;
         }
      
      return something_changed;
      }

   /**
    * Tries to reduce the nets of traces and vias, so that the nets are a subset of the nets of the contact items. 
    * This is applied to traces and vias with more than 1 net connected to tie pins. 
    */
   public void reduce_nets_of_route_items()
      {
      int reduce_loop_counter=0;
      
      while (reduce_nets_of_route_items_changed())
         {
         if ( reduce_loop_counter++ > 100 )
            {
            // lets avoid possibly forever spinning loops
            userPrintln("reduce_nets_of_route_items: Excessive loops");
            break;
            }
         }
      }

   /**
    * Returns the obstacle responsible for the last shove to fail.
    */
   public BrdItem shove_fail_obstacle_get()
      {
      return shove_obstacle.brd_item;
      }

   public void shove_fail_obstacle_set(BrdItem p_item)
      {
      shove_obstacle.brd_item = p_item;
      }

   public int shove_fail_layer_get()
      {
      return shove_obstacle.on_layer;
      }

   public void shove_fail_layer_set(int p_layer)
      {
      shove_obstacle.on_layer = p_layer;
      }

   private void shove_fail_clear()
      {
      shove_obstacle.clear();
      }
   
   public final boolean debug ( int p_mask, int p_level )
      {
      return stat.debug(p_mask, p_level);
      }
   
   public final void userPrintln( String message )
      {
      stat.userPrintln(message);
      }

   public final void userPrintln( String message, Exception exc )
      {
      stat.userPrintln(message, exc);
      }
   
   public final GuiResources newGuiResources ( String key )
      {
      return new GuiResources (stat, key);
      }
   
   /**
    * Used to have some meaningful info on this object
    * Mostly used for beanshell
    */
   @Override
   public String toString()
      {
      StringBuilder risul = new StringBuilder(1000);
      risul.append("RoutingBoard \n");
      risul.append("object: search_tree_manager \n");
      
      return risul.toString();
      }

   }
