package com.miaodi.note.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.miaodi.note.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.tvVersion.text = "当前版本 ${getVersionName()}"

        binding.rowPrivacy.setOnClickListener { showDevToast("隐私政策") }
        binding.rowAgreement.setOnClickListener { showDevToast("用户协议") }
        binding.rowWebsite.setOnClickListener { showDevToast("软件官网") }
        binding.rowDeveloper.setOnClickListener { copyAndToast("Jalor", "开发者已复制") }
        binding.rowWechat.setOnClickListener { copyAndToast("Libv", "公众号已复制") }
        binding.rowQQGroup.setOnClickListener {
            copyAndToast("miaodi-note", "Q群号已复制")
        }
    }

    private fun getVersionName(): String {
        return try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
                .versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }

    private fun copyAndToast(text: String, msg: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("about", text))
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun showDevToast(name: String) {
        Toast.makeText(requireContext(), "$name", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}