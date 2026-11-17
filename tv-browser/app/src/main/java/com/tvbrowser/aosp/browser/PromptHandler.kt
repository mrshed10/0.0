package com.tvbrowser.aosp.browser

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import com.tvbrowser.aosp.R
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/**
 * GeckoView renders no native dialogs by itself: alert(), confirm(), prompt(), <select>
 * drop-downs and HTTP auth all come here. All dialogs are plain D-pad friendly AlertDialogs.
 */
class PromptHandler(private val activity: Activity) : PromptDelegate {

    private fun builder() = AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)

    /** Completes a result exactly once (dialog may be both clicked and dismissed). */
    private class Once(val result: GeckoResult<PromptResponse>) {
        private var done = false
        fun complete(r: PromptResponse) {
            if (!done) {
                done = true
                result.complete(r)
            }
        }
    }

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt): GeckoResult<PromptResponse>? {
        if (activity.isFinishing) return GeckoResult.fromValue(prompt.dismiss())
        val once = Once(GeckoResult())
        builder()
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setPositiveButton(android.R.string.ok) { _, _ -> once.complete(prompt.dismiss()) }
            .setOnDismissListener { once.complete(prompt.dismiss()) }
            .show()
        return once.result
    }

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt): GeckoResult<PromptResponse>? {
        if (activity.isFinishing) return GeckoResult.fromValue(prompt.dismiss())
        val once = Once(GeckoResult())
        builder()
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                once.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                once.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE))
            }
            .setOnDismissListener { once.complete(prompt.dismiss()) }
            .show()
        return once.result
    }

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt): GeckoResult<PromptResponse>? {
        if (activity.isFinishing) return GeckoResult.fromValue(prompt.dismiss())
        val once = Once(GeckoResult())
        val input = EditText(activity).apply {
            setText(prompt.defaultValue)
            setSingleLine()
        }
        builder()
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> once.complete(prompt.confirm(input.text.toString())) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> once.complete(prompt.dismiss()) }
            .setOnDismissListener { once.complete(prompt.dismiss()) }
            .show()
        return once.result
    }

    override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt): GeckoResult<PromptResponse>? {
        if (activity.isFinishing) return GeckoResult.fromValue(prompt.dismiss())
        val once = Once(GeckoResult())
        val onlyPassword = (prompt.authOptions.flags and
            PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
        val user = EditText(activity).apply {
            hint = activity.getString(R.string.username)
            setSingleLine()
            setText(prompt.authOptions.username)
        }
        val pass = EditText(activity).apply {
            hint = activity.getString(R.string.password)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            if (!onlyPassword) addView(user)
            addView(pass)
        }
        builder()
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val r = if (onlyPassword) prompt.confirm(pass.text.toString())
                else prompt.confirm(user.text.toString(), pass.text.toString())
                once.complete(r)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> once.complete(prompt.dismiss()) }
            .setOnDismissListener { once.complete(prompt.dismiss()) }
            .show()
        return once.result
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: PromptDelegate.BeforeUnloadPrompt,
    ): GeckoResult<PromptResponse>? = GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))

    /** HTML <select>, menus and multi-selects. */
    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse>? {
        if (activity.isFinishing) return GeckoResult.fromValue(prompt.dismiss())
        val flat = ArrayList<PromptDelegate.ChoicePrompt.Choice>()
        val labels = ArrayList<String>()
        fun collect(choices: Array<PromptDelegate.ChoicePrompt.Choice>, group: String?) {
            for (c in choices) {
                if (c.separator) continue
                val children = c.items
                if (children != null) {
                    collect(children, c.label)
                } else if (!c.disabled) {
                    flat += c
                    labels += if (group.isNullOrBlank()) c.label else "$group › ${c.label}"
                }
            }
        }
        collect(prompt.choices, null)

        val once = Once(GeckoResult())
        val b = builder().setTitle(prompt.title)
        if (prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
            val checked = BooleanArray(flat.size) { flat[it].selected }
            b.setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val selected = flat.filterIndexed { i, _ -> checked[i] }.toTypedArray()
                    once.complete(prompt.confirm(selected))
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> once.complete(prompt.dismiss()) }
        } else {
            val selectedIndex = flat.indexOfFirst { it.selected }
            b.setSingleChoiceItems(labels.toTypedArray(), selectedIndex) { d, which ->
                once.complete(prompt.confirm(flat[which]))
                d.dismiss()
            }
        }
        b.setOnDismissListener { once.complete(prompt.dismiss()) }
        b.show()
        return once.result
    }
}
