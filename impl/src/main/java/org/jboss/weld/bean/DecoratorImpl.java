/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.bean;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.inject.spi.Decorator;
import javax.inject.Inject;

import org.jboss.weld.BeanManagerImpl;
import org.jboss.weld.DefinitionException;
import org.jboss.weld.bootstrap.BeanDeployerEnvironment;
import org.jboss.weld.injection.MethodInjectionPoint;
import org.jboss.weld.injection.WeldInjectionPoint;
import org.jboss.weld.introspector.WeldClass;

public class DecoratorImpl<T> extends ManagedBean<T> implements Decorator<T>
{

   public static <T> Decorator<T> wrap(final Decorator<T> decorator)
   {
      return new ForwardingDecorator<T>()
      {

         @Override
         public Set<Annotation> getQualifiers()
         {
            return delegate().getDelegateQualifiers();
         }

         @Override
         public Set<Type> getTypes()
         {
            return delegate().getTypes();
         }

         @Override
         protected Decorator<T> delegate()
         {
            return decorator;
         }

      };
   }

   /**
    * Creates a decorator bean
    * 
    * @param <T> The type
    * @param clazz The class
    * @param manager the current manager
    * @return a Bean
    */
   public static <T> DecoratorImpl<T> of(WeldClass<T> clazz, BeanManagerImpl manager)
   {
      return new DecoratorImpl<T>(clazz, manager);
   }

   private WeldInjectionPoint<?, ?> delegateInjectionPoint;
   private Set<Annotation> delegateBindings;
   private Type delegateType;
   private Set<Type> delegateTypes;
   private Set<Type> decoratedTypes;

   protected DecoratorImpl(WeldClass<T> type, BeanManagerImpl manager)
   {
      super(type, new StringBuilder().append(Decorator.class.getSimpleName()).append(BEAN_ID_SEPARATOR).append(type.getName()).toString(), manager);
   }

   @Override
   public void initialize(BeanDeployerEnvironment environment)
   {
      if (!isInitialized())
      {
         super.initialize(environment);
         initDelegateInjectionPoint();
         initDecoratedTypes();
         initDelegateBindings();
         initDelegateType();
         checkDelegateType();
      }
   }

   protected void initDecoratedTypes()
   {
      this.decoratedTypes = new HashSet<Type>();
      this.decoratedTypes.addAll(getAnnotatedItem().getInterfaceOnlyFlattenedTypeHierarchy());
      this.decoratedTypes.remove(Serializable.class);
   }

   protected void initDelegateInjectionPoint()
   {
      this.delegateInjectionPoint = getDelegateInjectionPoints().iterator().next();
   }

   @Override
   protected void checkDelegateInjectionPoints()
   {
      for (WeldInjectionPoint<?, ?> injectionPoint : getDelegateInjectionPoints())
      {
         if (injectionPoint instanceof MethodInjectionPoint<?, ?> && !injectionPoint.isAnnotationPresent(Inject.class))
         {
            throw new DefinitionException("Method with @Decorates parameter must be an initializer method " + injectionPoint);
         }
      }
      if (getDelegateInjectionPoints().size() == 0)
      {
         throw new DefinitionException("No delegate injection points defined " + this);
      }
      else if (getDelegateInjectionPoints().size() > 1)
      {
         throw new DefinitionException("Too many delegate injection point defined " + this);
      }
   }

   protected void initDelegateBindings()
   {
      this.delegateBindings = new HashSet<Annotation>(); 
      this.delegateBindings.addAll(this.delegateInjectionPoint.getQualifiers());
   }

   protected void initDelegateType()
   {
      this.delegateType = this.delegateInjectionPoint.getBaseType();
      this.delegateTypes = new HashSet<Type>();
      delegateTypes.add(delegateType);
   }

   protected void checkDelegateType()
   {
      for (Type decoratedType : getDecoratedTypes())
      {
         if (decoratedType instanceof Class)
         {
            if (!((Class<?>) decoratedType).isAssignableFrom(delegateInjectionPoint.getJavaClass()))
            {
               throw new DefinitionException("The delegate type must extend or implement every decorated type. Decorated type " + decoratedType + "." + this );
            }
         }
         else if (decoratedType instanceof ParameterizedType)
         {
            ParameterizedType parameterizedType = (ParameterizedType) decoratedType;
            if (!delegateInjectionPoint.isParameterizedType())
            {
               throw new DefinitionException("The decorated type is parameterized, but the delegate type isn't. Delegate type " + delegateType + "." + this);
            }
            if (!Arrays.equals(delegateInjectionPoint.getActualTypeArguments(), parameterizedType.getActualTypeArguments()))
            {
               throw new DefinitionException("The delegate type must have exactly the same type parameters as the decorated type. Decorated type " + decoratedType + "." + this );
            }
            Type rawType = ((ParameterizedType) decoratedType).getRawType();
            if (rawType instanceof Class && !((Class<?>) rawType).isAssignableFrom(delegateInjectionPoint.getJavaClass()))
            {
               throw new DefinitionException("The delegate type must extend or implement every decorated type. Decorated type " + decoratedType + "." + this );
            }
            else
            {
               throw new IllegalStateException("Unable to process " + decoratedType);
            }

         }
      }
   }

   public Set<Annotation> getDelegateQualifiers()
   {
      return delegateBindings;
   }

   public Type getDelegateType()
   {
      return delegateType;
   }

   public Set<Type> getDecoratedTypes()
   {
      return decoratedTypes;
   }
   
   public WeldInjectionPoint<?, ?> getDelegateInjectionPoint()
   {
      return delegateInjectionPoint;
   }
   
   @Override
   public void initDecorators()
   {
      // No-op, decorators can't have decorators
   }
   
   @Override
   public String getDescription()
   {
      // TODO Auto-generated method stub
      return super.getDescription("decorator");
   }

}
