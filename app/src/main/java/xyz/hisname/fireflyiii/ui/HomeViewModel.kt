/*
 * Copyright (c)  2018 - 2021 Daniel Quah
 * Copyright (c)  2021 ASDF Dev Pte. Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.hisname.fireflyiii.ui

import android.accounts.AccountManager
import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.hisname.fireflyiii.Constants
import xyz.hisname.fireflyiii.data.local.account.NewAccountManager
import xyz.hisname.fireflyiii.data.local.account.OldAuthenticatorManager
import xyz.hisname.fireflyiii.data.local.dao.AppDatabase
import xyz.hisname.fireflyiii.data.local.dao.FireflyUserDatabase
import xyz.hisname.fireflyiii.data.local.pref.AppPref
import xyz.hisname.fireflyiii.data.remote.firefly.FireflyClient
import xyz.hisname.fireflyiii.data.remote.firefly.api.BillsService
import xyz.hisname.fireflyiii.repository.BaseViewModel
import xyz.hisname.fireflyiii.repository.bills.BillRepository
import xyz.hisname.fireflyiii.repository.bills.BillsPaidRepository
import xyz.hisname.fireflyiii.repository.models.FireflyUsers
import xyz.hisname.fireflyiii.util.DateTimeUtil
import java.io.File
import java.util.*

class HomeViewModel(application: Application): BaseViewModel(application) {

    private val billsService = genericService().create(BillsService::class.java)
    private val billDataDao = AppDatabase.getInstance(application, getUniqueHash()).billDataDao()
    private val billPaidDao = AppDatabase.getInstance(application, getUniqueHash()).billPaidDao()
    private val billPayDao = AppDatabase.getInstance(application, getUniqueHash()).billPayDao()
    private val billRepository = BillRepository(billDataDao, billsService)
    private val billPaidRepository = BillsPaidRepository(billPaidDao, billsService)
    private val fireflyUserDatabase by lazy { FireflyUserDatabase.getInstance(application).fireflyUserDao() }
    val userEmail = getActiveUserEmail()

    fun updateActiveUser(userEmail: String, userHost: String){
        fireflyUserDatabase.updateActiveUser(userEmail, userHost)
    }

    fun getNoOfBillsDueToday(): LiveData<Int> {
        val count = MutableLiveData<Int>()
        viewModelScope.launch(Dispatchers.IO){
            val billDue = billRepository.getBillDueFromDate(DateTimeUtil.getTodayDate())
            val billPaidId = billPaidRepository.getBillPaidByDate(DateTimeUtil.getTodayDate(),
                    DateTimeUtil.getTodayDate(), billPayDao)
            val billDueId = arrayListOf<Long>()
            billDue.forEach {  billData ->
                billDueId.add(billData.billId)
            }
            val billIdDifference = billDueId.minus(billPaidId)
            count.postValue(billIdDifference.size)
        }
        return count
    }

    fun getFireflyUsers(): List<FireflyUsers>{
        return fireflyUserDatabase.getAllUser()
    }

    fun removeFireflyAccounts(listOfAccounts: List<Int>){
        if (listOfAccounts.isNotEmpty()){
            listOfAccounts.forEach { account ->
                val fireflyUser = fireflyUserDatabase.getUerByPrimaryKey(account.toLong())
                fireflyUser.activeUser
                File(getApplication<Application>().applicationInfo.dataDir + "/shared_prefs/" + fireflyUser.uniqueHash
                        + "-user-preferences.xml").delete()
                fireflyUserDatabase.deleteUserByPrimaryKey(account.toLong())
                // TODO: 1. Remove account from account manager here
                //       2. Check if default account is deleted. If it is deleted, set first row in database as default
            }
            FireflyClient.destroyInstance()
        }

    }

    fun migrateFirefly(){
        /* Migration routine:
         * 1. Check if old database exists(firefly.db)
         *      - If it exists rename it to user's email-photuris.db(demo@firefly-photuris.db)
         *      - If it does not exist, user is a new user
         * 2. Rename user's account
         * 3. Rename shared preference
         * 4. Rename custom CA file(if it exists)
         */
        val application = getApplication<Application>()
        val oldDatabase = application.getDatabasePath(Constants.DB_NAME)
        val accManager = OldAuthenticatorManager(AccountManager.get(getApplication()))
        val uniqueHash = UUID.randomUUID().toString()
        val authEmail = accManager.userEmail
        if (oldDatabase.exists()){
            AppDatabase.destroyInstance()
            oldDatabase.renameTo(File(application.getDatabasePath("$uniqueHash-photuris.db").toString()))
            oldDatabase.delete()
            FireflyClient.destroyInstance()
        }
        if(!accManager.userEmail.isNullOrBlank()){
            val accountSecretKey = accManager.secretKey
            val accountAccessToken = accManager.accessToken
            val accountClientId = accManager.clientId
            // This is throwing NULL for some reason
           // val accountTokenExpiry = accManager.tokenExpiry
            val accountRefreshToken = accManager.refreshToken
            val accountAuthMethod = accManager.authMethod
            accManager.destroyAccount()
            val newAccountManager = NewAccountManager(AccountManager.get(application), authEmail)
            newAccountManager.initializeAccount()
            newAccountManager.secretKey = accountSecretKey
            newAccountManager.accessToken = accountAccessToken
            newAccountManager.clientId = accountClientId
            newAccountManager.refreshToken = accountRefreshToken
            newAccountManager.authMethod = accountAuthMethod
            // TODO: Fix this before releasing
            //newAccountManager.tokenExpiry = accountTokenExpiry
            newAccountManager.userEmail = authEmail
            viewModelScope.launch(Dispatchers.IO){
                fireflyUserDatabase.insert(
                    FireflyUsers(
                        0L, uniqueHash, authEmail, AppPref(sharedPref()).baseUrl, true
                    )
                )
            }
        }
        val fileArray = File(application.applicationInfo.dataDir + "/shared_prefs").listFiles()
        fileArray?.forEach {  file ->
            if(file.name.startsWith("xyz.hisname.fireflyiii") && file.name.endsWith("preferences.xml")){
                file.renameTo(File(application.applicationInfo.dataDir + "/shared_prefs/" + getUniqueHash() + "-user-preferences.xml"))
            }
        }
        val customCaFile = File(getApplication<Application>().filesDir.path + "/user_custom.pem")
        if(customCaFile.exists()){
            customCaFile.renameTo(File(getApplication<Application>().filesDir.path + "/" + getUniqueHash() + ".pem"))
            customCaFile.delete()
        }
    }
}