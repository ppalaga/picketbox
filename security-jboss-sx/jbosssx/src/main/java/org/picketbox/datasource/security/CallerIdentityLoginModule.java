/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.picketbox.datasource.security;

import java.security.Principal;
import java.security.acl.Group;
import java.util.Map;
import java.util.Set;

import javax.resource.spi.security.PasswordCredential;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;

import org.jboss.logging.Logger;
import org.jboss.security.ErrorCodes;
import org.jboss.security.RunAsIdentity;
import org.jboss.security.SimplePrincipal;

/**
 * A simple login module that simply associates the principal making the
 * connection request with the actual EIS connection requirements.
 *
 * The type of Principal class used is
 * <code>org.jboss.security.SimplePrincipal.</code>
 * <p>
 *
 * @see org.picketbox.datasource.security.ConfiguredIdentityLoginModule
 *
 * @author Scott.Stark@jboss.org
 * @author <a href="mailto:d_jencks@users.sourceforge.net">David Jencks</a>
 * @author <a href="mailto:dan.bunker@pbs.proquest.com">Dan Bunker</a>
 * @version $Revision: 71545 $
 */

@SuppressWarnings("unchecked")
public class CallerIdentityLoginModule
   extends AbstractPasswordCredentialLoginModule
{
   /**
    * Class logger
    */
   private static final Logger log = Logger.getLogger(CallerIdentityLoginModule.class);

   private boolean trace = log.isTraceEnabled();
   
   /**
    * The default username/principal to use for basic connections
    */
   private String userName;

   /**
    * The default password to use for basic connections
    */
   private char[] password;
   /** A flag indicating if the run-as principal roles should be added to the subject */
   private boolean addRunAsRoles;
   private Set<Principal> runAsRoles;

   /**
    * Default Constructor
    */
   public CallerIdentityLoginModule()
   {
   }

   /**
    * The initialize method sets up some default connection information for
    * basic connections.  This is useful for container initialization connection
    * use or running the application in a non-secure manner.  This method is
    * called before the login method.
    *
    * @param subject
    * @param handler
    * @param sharedState
    * @param options
    */
   @Override
   public void initialize(Subject subject, CallbackHandler handler, Map<String, ?> sharedState, Map<String, ?> options)
   {
      super.initialize(subject, handler, sharedState, options);

      userName = (String) options.get("userName");
      if (userName == null)
      {
         log.debug("No default username supplied.");
      }

      String pass = (String) options.get("password");
      if (pass == null)
      {
         log.debug("No default password supplied.");
      }
      else
      {
         password = pass.toCharArray();
      }

      // Check the addRunAsRoles
      String flag = (String) options.get("addRunAsRoles");
      addRunAsRoles = Boolean.valueOf(flag).booleanValue();

      log.debug("got default principal: " + userName + ", username: "
         + userName + ", password: " + (password == null ? "null" : "****")
         + " addRunAsRoles: "+addRunAsRoles);

   }

   /**
    * Performs the login association between the caller and the resource for a
    * 1 to 1 mapping.  This acts as a login propagation strategy and is useful
    * for single-sign on requirements
    *
    * @return True if authentication succeeds
    * @throws LoginException
    */
   @Override
   public boolean login() throws LoginException
   {
      if(trace)
         log.trace("Caller Association login called");

      //setup to use the default connection info.  This will be overiden if security
      //associations are found
      String username = userName;

      //ask the security association class for the principal info making this request
      try
      {
         Principal user = GetPrincipalInfoAction.getPrincipal();
         char[] userPassword = GetPrincipalInfoAction.getCredential();

         if( userPassword != null )
         {
            password = userPassword;
         }

         if (user != null)
         {
            username = user.getName();
            if (trace)
            {
               log.trace("Current Calling principal is: " + username
                  + " ThreadName: " + Thread.currentThread().getName());
            }
            // Check for a RunAsIdentity
            RunAsIdentity runAs = GetPrincipalInfoAction.peekRunAsIdentity();
            if( runAs != null )
            {
               runAsRoles = runAs.getRunAsRoles();
            }
         }
      }
      catch (Throwable e)
      {
         throw new LoginException(ErrorCodes.PROCESSING_FAILED + "Unable to get the calling principal or its credentials for resource association");
      }

      // Update userName so that getIdentity is consistent
      userName = username;
      if (super.login() == true)
      {
         return true;
      }

      // Put the principal name into the sharedState map
      sharedState.put("javax.security.auth.login.name", username);
      super.loginOk = true;

      return true;
   }

   @Override
   public boolean commit() throws LoginException
   {
      // Put the principal name into the sharedState map
      sharedState.put("javax.security.auth.login.name", userName);
      // Add any run-as roles if addRunAsRoles is true
      if( addRunAsRoles && runAsRoles != null )
      {
         SubjectActions.addRoles(subject, runAsRoles);         
      }

      // Add the PasswordCredential
      PasswordCredential cred = new PasswordCredential(userName, password);
      SubjectActions.addCredentials(subject, cred);
      return super.commit();
   }

   protected Principal getIdentity()
   {
      if(trace)
         log.trace("getIdentity called");
      Principal principal = new SimplePrincipal(userName);
      return principal;
   }

   protected Group[] getRoleSets() throws LoginException
   {
      if(trace)
         log.trace("getRoleSets called");
      return new Group[]{};
   }
}
