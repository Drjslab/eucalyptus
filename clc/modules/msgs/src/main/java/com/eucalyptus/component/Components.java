/*******************************************************************************
 *Copyright (c) 2009 Eucalyptus Systems, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, only version 3 of the License.
 * 
 * 
 * This file is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Please contact Eucalyptus Systems, Inc., 130 Castilian
 * Dr., Goleta, CA 93101 USA or visit <http://www.eucalyptus.com/licenses/>
 * if you need additional information or have any questions.
 * 
 * This file may incorporate work covered under the following copyright and
 * permission notice:
 * 
 * Software License Agreement (BSD License)
 * 
 * Copyright (c) 2008, Regents of the University of California
 * All rights reserved.
 * 
 * Redistribution and use of this software in source and binary forms, with
 * or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. USERS OF
 * THIS SOFTWARE ACKNOWLEDGE THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE
 * LICENSED MATERIAL, COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS
 * SOFTWARE, AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 * IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA, SANTA
 * BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY, WHICH IN
 * THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION, REPLACEMENT
 * OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO IDENTIFIED, OR
 * WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT NEEDED TO COMPLY WITH
 * ANY SUCH LICENSES OR RIGHTS.
 *******************************************************************************
 * @author chris grzegorczyk <grze@eucalyptus.com>
 */
package com.eucalyptus.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.log4j.Logger;
import com.eucalyptus.bootstrap.Bootstrap;
import com.eucalyptus.bootstrap.BootstrapException;
import com.eucalyptus.bootstrap.Bootstrapper;
import com.eucalyptus.bootstrap.SystemBootstrapper;
import com.eucalyptus.component.id.Eucalyptus;
import com.eucalyptus.records.EventRecord;
import com.eucalyptus.records.EventType;
import com.eucalyptus.util.HasName;
import com.eucalyptus.util.LogUtil;
import com.eucalyptus.util.Mbeans;
import com.eucalyptus.util.async.Callback;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class Components {
  private static Logger                           LOG                  = Logger
                                                                                                                                      .getLogger( Components.class );
  private static ConcurrentMap<Class, Map>        componentInformation = new ConcurrentHashMap<Class, Map>( ) {
                                                                         {
                                                                           put( MessagableService.class, new ConcurrentHashMap<String, MessagableService>( ) );
                                                                           put( Component.class, new ConcurrentHashMap<String, Component>( ) );
                                                                           put( ComponentId.class, new ConcurrentHashMap<String, ComponentId>( ) );
                                                                         }
                                                                       };
  
  private static Function<Component, ComponentId> TO_ID                = new Function<Component, ComponentId>( ) {
                                                                         
                                                                         @Override
                                                                         public ComponentId apply( Component input ) {
                                                                           return input.getComponentId( );
                                                                         }
                                                                       };
  
  public static List<ComponentId> toIds( List<Component> components ) {
    return Lists.transform( components, TO_ID );
  }
  
  private static Predicate<Component> BOOTSTRAP_LOCALS = new Predicate<Component>( ) {
                                                         
                                                         @Override
                                                         public boolean apply( Component c ) {
                                                           return ComponentIds.shouldBootstrapLocally( c.getComponentId( ) );
                                                         }
                                                       };
  
  /**
   * Components which are staticly determined as ones to load. This determination is made
   * independent of access to the database; i.e. only the command line flags and presence/absence of
   * files determines this list.
   * 
   * @return
   */
  public static List<Component> whichCanLoad( ) {
    return Lists.newArrayList( Iterables.filter( Components.list( ), BOOTSTRAP_LOCALS ) );
  }
  
  private static Predicate<Component> ARE_ENABLED_LOCAL = new Predicate<Component>( ) {
                                                          
                                                          @Override
                                                          public boolean apply( Component c ) {
                                                            boolean cloudLocal = Bootstrap.isCloudController( ) && c.getComponentId( ).isCloudLocal( );
                                                            boolean alwaysLocal = c.getComponentId( ).isAlwaysLocal( );
                                                            boolean runningLocal = c.hasServiceEnabled( );
                                                            return cloudLocal || alwaysLocal || runningLocal;
                                                          }
                                                        };
  
  /**
   * Component has a service instance which is present locally, independent of the service's state.
   * 
   * @return
   */
  public static List<Component> whichAreEnabledLocally( ) {
    return Lists.newArrayList( Iterables.filter( Components.list( ), ARE_ENABLED_LOCAL ) );
  }
  
  private static Predicate<Component> ARE_ENABLED = new Predicate<Component>( ) {
                                                    
                                                    @Override
                                                    public boolean apply( Component c ) {
                                                      return c.hasServiceEnabled( );
                                                    }
                                                  };
  
  /**
   * Component has a service instance which is present locally and the service is ENABLED.
   * 
   * @return
   */
  public static List<Component> whichAreEnabled( ) {
    return Lists.newArrayList( Iterables.filter( Components.list( ), ARE_ENABLED ) );
  }
  
  @SuppressWarnings( "unchecked" )
  public static List<Component> list( ) {//TODO:GRZE:ASAP: review all usage of this and replace with Components.whichAre...
    return new ArrayList( Components.lookupMap( Component.class ).values( ) );
  }
  
  private static <T extends HasName<T>> Class getRealType( Class<T> maybeSubclass ) {
    Class type = null;
    for ( Class c : componentInformation.keySet( ) ) {
      if ( c.isAssignableFrom( maybeSubclass ) ) {
        type = c;
        return type;
      }
    }
    Components.dumpState( );
    throw BootstrapException.throwFatal( "Failed bootstrapping component registry.  Missing entry for component info type: " + maybeSubclass.getSimpleName( ) );
  }
  
  static <T> Map<String, T> lookupMap( Class type ) {
    return ( Map<String, T> ) componentInformation.get( getRealType( type ) );
  }
  
  static void dumpState( ) {
    for ( Class c : componentInformation.keySet( ) ) {
      for ( Entry<String, ComponentInformation> e : ( Set<Entry<String, ComponentInformation>> ) componentInformation.get( c ).entrySet( ) ) {
        LOG.info( EventRecord.here( Bootstrap.class, EventType.COMPONENT_REGISTRY_DUMP, c.getSimpleName( ), e.getKey( ), e.getValue( ).getClass( )
                                                                                                                          .getCanonicalName( ) ) );
      }
    }
  }
  
  public static <T extends HasName<T>> boolean contains( Class<T> type, String name ) {
    return Components.lookupMap( type ).containsKey( name );
  }
  
  private static <T extends HasName<T>> void remove( T componentInfo ) {
    Map<String, T> infoMap = lookupMap( componentInfo.getClass( ) );
    infoMap.remove( componentInfo.getName( ) );
  }
  
  private static <T extends HasName<T>> void put( T componentInfo ) {
    Map<String, T> infoMap = lookupMap( componentInfo.getClass( ) );
    if ( infoMap.containsKey( componentInfo.getName( ) ) ) {
      throw BootstrapException.throwFatal( "Failed bootstrapping component registry.  Duplicate information for component '" + componentInfo.getName( ) + "': "
                                           + componentInfo.getClass( ).getSimpleName( ) + " as " + getRealType( componentInfo.getClass( ) ) );
    } else {
      infoMap.put( componentInfo.getName( ), componentInfo );
    }
  }
  
  public static <T extends HasName<T>> void deregister( T componentInfo ) {
    remove( componentInfo );
    if ( Component.class.isAssignableFrom( componentInfo.getClass( ) ) ) {
      EventRecord.here( Bootstrap.class, EventType.COMPONENT_DEREGISTERED, componentInfo.toString( ) ).info( );
    } else {
      EventRecord.here( Bootstrap.class, EventType.COMPONENT_DEREGISTERED, componentInfo.getName( ), componentInfo.getClass( ).getSimpleName( ) ).trace( );
    }
  }
  
  static <T extends HasName<T>> void register( T componentInfo ) {
    if ( !contains( componentInfo.getClass( ), componentInfo.getName( ) ) ) {
      if ( Component.class.isAssignableFrom( componentInfo.getClass( ) ) ) {
        EventRecord.here( Bootstrap.class, EventType.COMPONENT_REGISTERED, componentInfo.toString( ) ).info( );
      } else {
        EventRecord.here( Bootstrap.class, EventType.COMPONENT_REGISTERED, componentInfo.getName( ), componentInfo.getClass( ).getSimpleName( ) ).trace( );
      }
      Components.put( componentInfo );
    }
  }
  
  public static <T extends HasName<T>> T lookup( Class<T> type, String name ) throws NoSuchElementException {
    if ( !contains( type, name ) ) {
      try {
        ComponentId compId = ComponentIds.lookup( name );
        Components.create( compId );
        return Components.lookup( type, name );
      } catch ( ServiceRegistrationException ex ) {
        throw new NoSuchElementException( "Missing entry for component '" + name + "' info type: " + type.getSimpleName( ) + " ("
                                          + getRealType( type ).getCanonicalName( ) );
      }
    } else {
      return ( T ) Components.lookupMap( type ).get( name );
    }
  }
  
  public static Component lookup( String componentName ) throws NoSuchElementException {
    return Components.lookup( Component.class, componentName );
  }
  
  public static <T extends ComponentId> Component lookup( Class<T> componentId ) throws NoSuchElementException {
    return Components.lookup( ComponentIds.lookup( componentId ) );
  }
  
  public static Component lookup( ComponentId componentId ) throws NoSuchElementException {
    return Components.lookup( Component.class, componentId.getName( ) );
  }
  
  public static boolean contains( String componentName ) {
    return Components.contains( Component.class, componentName );
  }
  
  public static Component create( ComponentId id ) throws ServiceRegistrationException {
    Component c = new Component( id );
    register( c );
    Mbeans.register( c );
    return c;
  }
  
  private final static Function<Component, String> componentToString = componentToString( );
  
  public static Function<Component, String> componentToString( ) {
    if ( componentToString != null ) {
      return componentToString;
    } else {
      synchronized ( Components.class ) {
        return new Function<Component, String>( ) {
          
          @Override
          public String apply( Component comp ) {
            final StringBuilder buf = new StringBuilder( );
            buf.append( LogUtil.header( comp.getName( ) + " component configuration" ) ).append( "\n" );
            buf.append( "-> Enabled/Local:      " + comp.isAvailableLocally( ) + "/" + comp.isRunningRemoteMode( ) ).append( "\n" );
            for ( Bootstrapper b : comp.getBootstrapper( ).getBootstrappers( ) ) {
              buf.append( "-> " + b.toString( ) ).append( "\n" );
            }
            buf.append( LogUtil.subheader( comp.getName( ) + " services" ) ).append( "\n" );
            for ( Service s : comp.getServices( ) ) {
              try {
                buf.append( "->  Service:          " + s.getFullName( ) + " " + s.getServiceConfiguration( ).getUri( ) ).append( "\n" );
                buf.append( "|-> Service config:   " + LogUtil.dumpObject( s.getServiceConfiguration( ) ) ).append( "\n" );
              } catch ( Exception ex ) {
                LOG.error( ex , ex );
              }
            }
            return buf.toString( );
          }
        };
      }
    }
  }
  
  public static Component oneWhichHandles( Class c ) {
    return ServiceBuilderRegistry.handles( c ).getComponent( );
  }
  
  private static final Callback.Success<Component> componentPrinter = componentPrinter( );
  
  public static Callback.Success<Component> componentPrinter( ) {
    if ( componentPrinter != null ) {
      return componentPrinter;
    } else {
      synchronized ( Components.class ) {
        return new Callback.Success<Component>( ) {
          
          @Override
          public void fire( Component comp ) {
            LOG.info( componentToString.apply( comp ) );
          }
        };
      }
    }
  }
  
  private static final Function<Bootstrapper, String> bootstrapperToString = new Function<Bootstrapper, String>( ) {
                                                                             @Override
                                                                             public String apply( Bootstrapper b ) {
                                                                               return b.getClass( ).getName( )
                                                                                       + " provides=" + b.getProvides( )
                                                                                       + " deplocal=" + b.getDependsLocal( )
                                                                                       + " depremote=" + b.getDependsRemote( );
                                                                             }
                                                                           };
  
}
