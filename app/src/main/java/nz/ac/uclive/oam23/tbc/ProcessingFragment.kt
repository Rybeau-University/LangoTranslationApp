package nz.ac.uclive.oam23.tbc

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.android.volley.AuthFailureError
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.JsonParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException


class ProcessingFragment : Fragment() {

    private val viewModel: TranslationsViewModel by activityViewModels() {
        TranslationsViewModelFactory((activity?.application as TBCApplication).repository)
    }

    val recogniser = TextRecognition.getClient()
    lateinit var currentAction: TextView
    lateinit var path: String
    lateinit var requestQueue: RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity?)!!.supportActionBar!!.hide()
        savedInstanceState?.get("photoPath")?.let { Log.d("Test", it.toString()) }
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val mainActivity = activity as MainActivity
        mainActivity.setLocation(MainActivity.Location.PROCESSING)
        return inflater.inflate(R.layout.fragment_processing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentAction = view.findViewById(R.id.currentAction)
        currentAction.text = getString(R.string.recognising_text)
        arguments?.getString("photoPath")?.let { detectText(it) } ?: run {
            Toast.makeText(
                    requireActivity(),
                    getString(R.string.no_image),
                    Toast.LENGTH_LONG
            ).show()
            requireActivity().onBackPressed()
        }

        requestQueue = Volley.newRequestQueue(context)

    }

    override fun onStop() {
        super.onStop()
        val file = File(path)
        if (file.exists()){
            file.delete()
        }
        if (requestQueue != null) {
            requestQueue?.cancelAll(viewModel.REQUEST_TAG)
        }
    }

    private fun detectText(imagePath: String) {
        path = imagePath
        val image: InputImage
        try {
            image = InputImage.fromFilePath(requireContext(), ("file://$imagePath").toUri())
            recogniser.process(image)
                .addOnSuccessListener { visionText -> run {
                    processVisionText(visionText)
                }
                }
                .addOnFailureListener { e ->
                    run {
                        Toast.makeText(
                                requireActivity(),
                                getString(R.string.text_recognition_fail),
                                Toast.LENGTH_LONG
                        ).show()
                        e.printStackTrace()
                        requireActivity().onBackPressed()
                    }
                }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun processVisionText(text: Text){
        val blocks = text.textBlocks
        if(blocks.size < 1){
            Toast.makeText(requireActivity(), getString(R.string.no_text_found), Toast.LENGTH_LONG).show()
            requireActivity().onBackPressed()
        } else {
            val stringBuilder = StringBuilder()
            for (block in blocks){
                val string = block.text.replace("\n", " ")
                stringBuilder.append(string)
            }
            translateText(stringBuilder.toString())
        }
    }

    private fun translateText(text: String){
        currentAction.text = getString(R.string.translating_text)
        Log.d("Text", text)
        sendRequest(text)
    }

    fun sendRequest(text: String) {
        // TODO: Update to work with the language the user selects :)
        val url = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=de"
        val request: StringRequest =
                object : StringRequest(Method.POST, url, object : Response.Listener<String?> {
                    override fun onResponse(response: String?) {
                        if (response != null) {
                            Log.d("req", "Your Array Response " + response)

                            val parser = JsonParser()
                            val json = parser.parse(response).asJsonArray

                            Log.d("Text", json.get(0).asJsonObject.get("translations").asJsonArray.get(0).asJsonObject.get("text").asString)

                            val bundle = bundleOf("original_text" to text, "translated_text" to json.get(0).asJsonObject.get("translations").asJsonArray.get(0).asJsonObject.get("text").asString)
                            Navigation.findNavController(view!!).navigate(R.id.action_processingFragment_to_navigation_saveEdit, bundle)

                        } else {
                            Log.d("req", "Response is null")
                        }
                    }
                }, object : Response.ErrorListener {
                    override fun onErrorResponse(error: VolleyError) {
                        Log.d("req", "error is " + error)
                    }
                }) {

                    @Throws(AuthFailureError::class)
                    override fun getHeaders(): Map<String, String> {
                        val params: MutableMap<String, String> = HashMap()
                        params["Content-Type"] = "application/json; charset=UTF-8"
                        params["Ocp-Apim-Subscription-Key"] = viewModel.API_KEY
                        return params
                    }

                    override fun getBody(): ByteArray {
                        val jsonObject = JSONObject()
                        jsonObject.put("Text", text)
                        val jsonArray = JSONArray()
                        jsonArray.put(jsonObject)
                        return jsonArray.toString().toByteArray()
                    }
                }
        requestQueue?.add(request)
    }

}