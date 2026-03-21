/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package com.itsaky.androidide.fragments.onboarding

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.github.appintro.SlidePolicy
import com.itsaky.androidide.databinding.FragmentOnboardingDisclaimerBinding
import com.itsaky.androidide.fragments.FragmentWithBinding
import io.noties.markwon.Markwon
import org.slf4j.LoggerFactory
import java.io.InputStreamReader

/**
 * 引导页：免责与隐私协议声明
 * @author android_zero
 */
class DisclaimerFragment : FragmentWithBinding<FragmentOnboardingDisclaimerBinding>(FragmentOnboardingDisclaimerBinding::inflate), SlidePolicy {

    private var isAgreed = false

    companion object {
        private val logger = LoggerFactory.getLogger(DisclaimerFragment::class.java)

        @JvmStatic
        fun newInstance(context: Context): DisclaimerFragment {
            return DisclaimerFragment()
        }
    }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
      super.onViewCreated(view, savedInstanceState)

      val context = requireContext()
      val markwon = Markwon.create(context)
    
     val markdownContent = try {
        context.assets.open("docs/Disclaimer-Agreement.md").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        logger.error("Document read failed", e)
        "**Error:** Unable to load protocol files. Please check if 'assets/docs/Disclaimer-Agreement.md' exists."
    }

    markwon.setMarkdown(binding.markdownTextView, markdownContent)

    binding.agreeCheckbox.setOnCheckedChangeListener { _, isChecked ->
        isAgreed = isChecked
    }
}


    /**
     * 判断是否满足进入下一页条件
     */
    override val isPolicyRespected: Boolean
        get() = isAgreed

    /**
     * 若未授权尝试进入下一页时，底部弹出 Toast 拦截动作
     */
    override fun onUserIllegallyRequestedNextPage() {
        Toast.makeText(requireContext(), "Please read and agree to the Disclaimer and Privacy Agreement first.", Toast.LENGTH_SHORT).show()
    }
}