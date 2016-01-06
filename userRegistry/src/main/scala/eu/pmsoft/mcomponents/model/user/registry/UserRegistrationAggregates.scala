/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Paweł Cesar Sanjuan Szklarz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 *
 */

package eu.pmsoft.mcomponents.model.user.registry

import eu.pmsoft.domain.model.{ UserID, UserLogin, UserPassword }
import eu.pmsoft.mcomponents.eventsourcing.eventstore.EventStoreRead
import eu.pmsoft.mcomponents.eventsourcing.projection.{ AggregateProjection, AggregateProjections }
import eu.pmsoft.mcomponents.model.security.roles.RoleID

object UserRegistrationAggregates {

  def buildUser(aggId: UserAggregateId)(implicit eventStore: EventStoreRead[UserRegistrationDomain]): UserDataAgg =
    AggregateProjections.buildAggregate(eventStore, new UserDataAggregateProjection())(aggId)

}

case class UserDataAgg(profile: Option[UserProfile] = None)

case class UserProfile(
  uid:          UserID,
  login:        UserLogin,
  passwordHash: UserPassword,
  active:       Boolean      = false,
  roles:        Set[RoleID]  = Set()
)

class UserDataAggregateProjection extends AggregateProjection[UserRegistrationDomain, UserDataAgg] {
  override def zero(): UserDataAgg = UserDataAgg()

  override def projectEvent(state: UserDataAgg, event: UserRegistrationEvent): UserDataAgg = event match {
    case UserCreated(uid, login, passwordHash) => UserDataAgg(Some(UserProfile(uid, login, passwordHash)))
    case UserPasswordUpdated(userId, passwordHashNew) => state.copy(profile = state.profile.map(p =>
      p.copy(passwordHash = passwordHashNew)))
    case UserActiveStatusUpdated(userId, active) => state.copy(profile = state.profile.map(p =>
      p.copy(active = active)))
    case UserObtainedAccessRoles(userId, roles) => state.copy(profile = state.profile.map(p =>
      p.copy(roles = roles)))
  }
}