package xyz.hisname.fireflyiii.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.fragment_pat.*
import xyz.hisname.fireflyiii.R
import xyz.hisname.fireflyiii.data.local.pref.AppPref
import xyz.hisname.fireflyiii.repository.account.AccountsViewModel
import xyz.hisname.fireflyiii.ui.ProgressBar
import xyz.hisname.fireflyiii.util.extension.*

class PatFragment: Fragment() {

    private val progressOverlay by lazy { requireActivity().findViewById<View>(R.id.progress_overlay) }
    private val model by lazy { getViewModel(AccountsViewModel::class.java) }
    private lateinit var fireflyUrl: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.create(R.layout.fragment_pat, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fireflySignIn.setOnClickListener {
            hideKeyboard()
            if(firefly_url_edittext.isBlank() or firefly_secret_edittext.isBlank()){
                if(firefly_url_edittext.isBlank()) {
                    firefly_url_edittext.showRequiredError()
                }
                if(firefly_secret_edittext.isBlank()){
                    firefly_secret_edittext.showRequiredError()
                }
            }  else {
                fireflyUrl = firefly_url_edittext.getString()
                AppPref(requireContext()).baseUrl = fireflyUrl
                AppPref(requireContext()).secretKey = firefly_secret_edittext.getString()
                model.getAllAccounts().observe(this, Observer { accountData ->
                    if(accountData != null){
                        AppPref(requireContext()).authMethod = "pat"
                        val frameLayout = requireActivity().findViewById<FrameLayout>(R.id.bigger_fragment_container)
                        frameLayout.removeAllViews()
                        requireActivity().supportFragmentManager.beginTransaction()
                                .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                                .add(R.id.bigger_fragment_container, OnboardingFragment())
                                .commit()
                        toastSuccess(resources.getString(R.string.welcome))
                    } else {
                        toastError(resources.getString(R.string.authentication_failed))
                    }
                })
                model.isLoading.observe(this, Observer {  loading ->
                    if(loading == true) {
                        ProgressBar.animateView(progressOverlay, View.VISIBLE, 0.4f, 200)
                    } else {
                        ProgressBar.animateView(progressOverlay, View.GONE, 0f, 200)
                    }
                })
            }

        }
    }
}