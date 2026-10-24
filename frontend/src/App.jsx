import { useEffect, useState } from 'react'

function App() {
  const [message, setMessage] = useState('Connecting to backend...')

  useEffect(() => {
    fetch('http://localhost:8080/api/hello')
      .then((response) => {
        if (!response.ok) {
          throw new Error(`HTTP error: ${response.status}`)
        }

        return response.text()
      })
      .then((data) => {
        setMessage(data)
      })
      .catch((error) => {
        setMessage(`Backend connection failed: ${error.message}`)
      })
  }, [])

  return (
    <main>
      <h1>Note Management System</h1>
      <p>{message}</p>
    </main>
  )
}

export default App
