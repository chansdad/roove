/*
 * Created by Andrii Kovalchuk
 * Copyright (c) 2020. All rights reserved.
 * Last modified 29.01.20 17:02
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.mmdev.data.user

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.iid.FirebaseInstanceId
import com.mmdev.business.user.BaseUserInfo
import com.mmdev.business.user.UserItem
import com.mmdev.business.user.repository.RemoteUserRepository
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

/**
 *
 * Remote FireBase structure
 * users -> city -> gender -> userDocument
 */

@Singleton
class UserRepositoryRemoteImpl @Inject constructor(private val fInstance: FirebaseInstanceId,
                                                   private val db: FirebaseFirestore,
                                                   private val localRepo: UserRepositoryLocal):
		RemoteUserRepository {

	companion object {
		private const val GENERAL_COLLECTION_REFERENCE = "users"
		private const val BASE_COLLECTION_REFERENCE = "usersBase"

		private const val TAG = "mylogs_UserRepoRemoteImpl"
	}

	override fun createUserOnRemote(userItem: UserItem): Completable {
		return Completable.create { emitter ->
			val ref = db.collection(GENERAL_COLLECTION_REFERENCE)
				.document(userItem.baseUserInfo.city)
				.collection(userItem.baseUserInfo.gender)
				.document(userItem.baseUserInfo.userId)
			fInstance.instanceId.addOnSuccessListener {instanceResult ->
				userItem.baseUserInfo.registrationTokens.add(instanceResult.token)
				ref.set(userItem)
					.addOnSuccessListener {
						db.collection(BASE_COLLECTION_REFERENCE)
							.document(userItem.baseUserInfo.userId)
							.set(userItem.baseUserInfo)
						emitter.onComplete()
					}
					.addOnFailureListener { emitter.onError(it) }
			}.addOnFailureListener { emitter.onError(it) }

		}.subscribeOn(Schedulers.io())
	}

	override fun deleteUser(userItem: UserItem): Completable {
		return Completable.create { emitter ->
			val ref = db.collection(GENERAL_COLLECTION_REFERENCE)
				.document(userItem.baseUserInfo.city)
				.collection(userItem.baseUserInfo.gender)
				.document(userItem.baseUserInfo.userId)
			ref.delete()
				.addOnSuccessListener {
					db.collection(BASE_COLLECTION_REFERENCE)
						.document(userItem.baseUserInfo.userId)
						.delete()
						.addOnCompleteListener { emitter.onComplete() }
						.addOnFailureListener { emitter.onError(it)  }
				}
				.addOnFailureListener { emitter.onError(it) }
		}.subscribeOn(Schedulers.io())
	}

	override fun fetchUserInfo(userItem: UserItem): Single<UserItem> {
		return Single.create(SingleOnSubscribe<UserItem> { emitter ->
			val ref = db.collection(GENERAL_COLLECTION_REFERENCE)
				.document(userItem.baseUserInfo.city)
				.collection(userItem.baseUserInfo.gender)
				.document(userItem.baseUserInfo.userId)
			ref.get()
				.addOnSuccessListener {
					val remoteUserItem = it.toObject(UserItem::class.java)!!
					fInstance.instanceId.addOnSuccessListener { instanceResult ->
						if (!remoteUserItem.baseUserInfo.registrationTokens.contains(instanceResult.token))
							remoteUserItem.baseUserInfo.registrationTokens.add(instanceResult.token)

						if (userItem == remoteUserItem) {
							Log.wtf(TAG, "no reason to fetch user")
							emitter.onSuccess(userItem)
						}
						//save new userItem
						else {
							ref.set(remoteUserItem)
								.addOnSuccessListener {
									db.collection(BASE_COLLECTION_REFERENCE)
										.document(userItem.baseUserInfo.userId)
										.set(remoteUserItem.baseUserInfo)
										.addOnSuccessListener {
											localRepo.saveUserInfo(remoteUserItem)
											emitter.onSuccess(remoteUserItem)
											Log.wtf(TAG, "user fetching result: {$remoteUserItem}")
											Log.wtf(TAG, "user sent to compare: {$userItem}")
										}
										.addOnFailureListener { updBaseErr -> emitter.onError(updBaseErr) }
								}
								.addOnFailureListener { updFullErr -> emitter.onError(updFullErr) }
						}
					}
					.addOnFailureListener { instanceIdError -> emitter.onError(instanceIdError) }
				}
				.addOnFailureListener { emitter.onError(it) }
		}).subscribeOn(Schedulers.io())
	}

	override fun getFullUserItem(baseUserInfo: BaseUserInfo): Single<UserItem> {
		return Single.create(SingleOnSubscribe<UserItem> { emitter ->
			val ref = db.collection(GENERAL_COLLECTION_REFERENCE)
				.document(baseUserInfo.city)
				.collection(baseUserInfo.gender)
				.document(baseUserInfo.userId)
			ref.get()
				.addOnSuccessListener { emitter.onSuccess(it.toObject(UserItem::class.java)!!) }
				.addOnFailureListener { emitter.onError(it) }
		}).subscribeOn(Schedulers.io())
	}

	override fun updateUserItem(userItem: UserItem): Completable {
		return Completable.create { emitter ->
			val ref = db.collection(GENERAL_COLLECTION_REFERENCE)
				.document(userItem.baseUserInfo.city)
				.collection(userItem.baseUserInfo.gender)
				.document(userItem.baseUserInfo.userId)
			ref.set(userItem)
				.addOnSuccessListener {
					db.collection(BASE_COLLECTION_REFERENCE)
						.document(userItem.baseUserInfo.userId)
						.set(userItem.baseUserInfo)
						.addOnCompleteListener {
							localRepo.saveUserInfo(userItem)
							emitter.onComplete()
						}
						.addOnFailureListener { emitter.onError(it) }
				}
				.addOnFailureListener { emitter.onError(it) }
		}.subscribeOn(Schedulers.io())
	}
}